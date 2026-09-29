// In-memory stand-in for the backend's species search, used when VITE_API_BASE is unset so the
// SPA runs with no backend. The data is the exact seed the backend's StubTaxonomyGateway serves
// (contexts/species/.../StubTaxonomyGateway.kt) — same five species, same IUCN statuses, same
// ids (`scientificName` lowercased, spaces → hyphens). The only fidelity trade is Elasticsearch's
// fuzzy ranking → a simple local matcher; that same swap is the design's "demo profile" story.
import type {
  BookingResponse,
  BookingResult,
  BookingStatus,
  CreateBookingRequest,
  ErrorResponse,
  HideAvailabilityResponse,
  HideResponse,
  SearchResult,
  SpeciesResponse,
} from './api'

const AVES = { kingdom: 'Animalia', phylum: 'Chordata', className: 'Aves' } as const

function species(
  scientificName: string,
  en: string,
  nl: string,
  order: string,
  family: string,
  genus: string,
  conservationStatus: string,
): SpeciesResponse {
  return {
    id: scientificName.toLowerCase().replace(/ /g, '-'),
    scientificName,
    commonNames: [
      { language: 'en', name: en },
      { language: 'nl', name: nl },
    ],
    taxonomy: { ...AVES, order, family, genus },
    conservationStatus,
  }
}

const SEED: readonly SpeciesResponse[] = [
  species('Turdus merula', 'Common Blackbird', 'Merel', 'Passeriformes', 'Turdidae', 'Turdus', 'LEAST_CONCERN'),
  species('Erithacus rubecula', 'European Robin', 'Roodborst', 'Passeriformes', 'Muscicapidae', 'Erithacus', 'LEAST_CONCERN'),
  species('Apus apus', 'Common Swift', 'Gierzwaluw', 'Apodiformes', 'Apodidae', 'Apus', 'LEAST_CONCERN'),
  species('Falco peregrinus', 'Peregrine Falcon', 'Slechtvalk', 'Falconiformes', 'Falconidae', 'Falco', 'LEAST_CONCERN'),
  species('Streptopelia turtur', 'European Turtle Dove', 'Tortelduif', 'Columbiformes', 'Columbidae', 'Streptopelia', 'VULNERABLE'),
]

/** A cheap fuzzy score: exact = 4, prefix = 3, substring = 2, in-order subsequence = 1, else 0. */
function score(haystack: string, needle: string): number {
  const h = haystack.toLowerCase()
  const n = needle.toLowerCase()
  if (h === n) return 4
  if (h.startsWith(n)) return 3
  if (h.includes(n)) return 2
  let i = 0
  for (const ch of h) if (ch === n[i]) i++
  return i === n.length ? 1 : 0
}

function bestScore(s: SpeciesResponse, needle: string): number {
  return Math.max(score(s.scientificName, needle), ...s.commonNames.map((c) => score(c.name, needle)))
}

/** Mirrors SpeciesSearchService: trims, requires ≥ 2 chars (else `InvalidQuery`), caps the limit. */
export function mockSearchSpecies(query: string, limit = 20): Promise<SearchResult> {
  const q = query.trim()
  if (q.length < 2) {
    const error: ErrorResponse = { code: 'InvalidQuery', message: 'query must be at least 2 characters' }
    return Promise.resolve({ ok: false, error })
  }
  const capped = Math.min(Math.max(limit, 1), 100)
  const matches = SEED.map((s) => ({ s, score: bestScore(s, q) }))
    .filter((m) => m.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, capped)
    .map((m) => m.s)
  // A small delay so the loading state is real (the backend would take a network round-trip).
  return new Promise((resolve) => setTimeout(() => resolve({ ok: true, species: matches }), 140))
}

// ---------------------------------------------------------------------------
// Hides + bookings — an in-memory store that mirrors the backend's concurrency
// semantics faithfully (optimistic-lock CAS, idempotency, the state machine and
// the slot-exclusivity check). The concurrency behaviour is the showpiece, so it
// is replicated, not simplified: two confirms carrying the same stale version
// yield one winner and one 409, exactly as the JDBC compare-and-swap would.
// ---------------------------------------------------------------------------

// Same ids + capacities the backend seeds in V301.
const HIDES: readonly HideResponse[] = [
  { id: '10000000-0000-0000-0000-000000000001', name: 'Kingfisher Hide', reserve: 'Leighton Moss', capacity: 6 },
  { id: '10000000-0000-0000-0000-000000000002', name: 'Osprey Platform', reserve: 'Loch Garten', capacity: 4 },
  { id: '10000000-0000-0000-0000-000000000003', name: 'Marsh Hide', reserve: 'Ham Wall', capacity: 8 },
  { id: '10000000-0000-0000-0000-000000000004', name: 'Bittern Screen', reserve: 'Minsmere', capacity: 5 },
]

const bookings = new Map<string, BookingResponse>()
const idempotency = new Map<string, string>()

const BOOKING_DELAY_MS = 120

function later<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), BOOKING_DELAY_MS))
}

function fail(code: string, message: string): { ok: false; error: ErrorResponse } {
  return { ok: false, error: { code, message } }
}

function uuid(): string {
  const c = globalThis.crypto
  if (typeof c !== 'undefined' && typeof c.randomUUID === 'function') return c.randomUUID()
  return 'xxxxxxxxxxxx4xxxyxxxxxxxxxxxxxxx'.replace(/[xy]/g, (ch) => {
    const r = (Math.random() * 16) | 0
    return (ch === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

const ms = (iso: string): number => new Date(iso).getTime()

function overlaps(aStart: string, aEnd: string, bStart: string, bEnd: string): boolean {
  return ms(aStart) < ms(bEnd) && ms(bStart) < ms(aEnd)
}

export function mockListHides(): Promise<readonly HideResponse[]> {
  return later(HIDES)
}

export function mockGetAvailability(hideId: string, from: string, to: string): Promise<HideAvailabilityResponse> {
  const hide = HIDES.find((h) => h.id === hideId)
  if (!hide) return Promise.reject(new Error(`hide not found: ${hideId}`))
  let occupied = 0
  for (const b of bookings.values()) {
    if (b.hideId === hideId && b.status === 'CONFIRMED' && overlaps(b.slotStart, b.slotEnd, from, to)) {
      occupied += b.partySize
    }
  }
  return later({
    hideId: hide.id,
    name: hide.name,
    reserve: hide.reserve,
    capacity: hide.capacity,
    occupied,
    seatsLeft: Math.max(hide.capacity - occupied, 0),
    from,
    to,
  })
}

export function mockCreateBooking(request: CreateBookingRequest): Promise<BookingResult> {
  const key = request.idempotencyKey
  if (key.length < 8 || key.length > 128 || !/^[A-Za-z0-9_-]+$/.test(key)) {
    return later(fail('InvalidIdempotencyKey', 'idempotency key must be 8–128 chars of letters, digits, - or _'))
  }
  // Idempotency: the same key returns the same booking, never a duplicate.
  const priorId = idempotency.get(key)
  if (priorId !== undefined) {
    const prior = bookings.get(priorId)
    if (prior) return later({ ok: true, booking: prior })
  }
  // Slot exclusivity: no overlapping active booking on the hide (mirrors hasOverlappingActiveBooking).
  for (const b of bookings.values()) {
    if (b.hideId === request.hideId && b.status !== 'CANCELLED' && overlaps(b.slotStart, b.slotEnd, request.slotStart, request.slotEnd)) {
      return later(fail('HideSlotUnavailable', `hide ${request.hideId} has an overlapping booking for the requested slot`))
    }
  }
  const ts = new Date().toISOString()
  const booking: BookingResponse = {
    id: uuid(),
    hideId: request.hideId,
    observerId: request.observerId,
    slotStart: request.slotStart,
    slotEnd: request.slotEnd,
    partySize: request.partySize,
    status: 'REQUESTED',
    version: 0,
    createdAt: ts,
    updatedAt: ts,
  }
  bookings.set(booking.id, booking)
  idempotency.set(key, booking.id)
  return later({ ok: true, booking })
}

function transition(
  id: string,
  expectedVersion: number,
  next: BookingStatus,
  allowedFrom: readonly BookingStatus[],
): Promise<BookingResult> {
  const current = bookings.get(id)
  if (!current) return later(fail('BookingNotFound', `booking not found: ${id}`))
  // Optimistic lock: the expected version must still match (the compare-and-swap).
  if (current.version !== expectedVersion) {
    return later(fail('VersionConflict', `optimistic lock: booking ${id} was modified concurrently (expected version ${expectedVersion})`))
  }
  if (!allowedFrom.includes(current.status)) {
    return later(fail('StateTransitionNotAllowed', `cannot transition booking ${id} from ${current.status} to ${next}`))
  }
  const updated: BookingResponse = {
    ...current,
    status: next,
    version: current.version + 1,
    updatedAt: new Date().toISOString(),
  }
  bookings.set(id, updated) // the store now holds the new version; older snapshots keep their version
  return later({ ok: true, booking: updated })
}

export function mockConfirmBooking(id: string, expectedVersion: number): Promise<BookingResult> {
  return transition(id, expectedVersion, 'CONFIRMED', ['REQUESTED'])
}

export function mockCancelBooking(id: string, expectedVersion: number): Promise<BookingResult> {
  return transition(id, expectedVersion, 'CANCELLED', ['REQUESTED', 'CONFIRMED'])
}

/** Test-only: clear the in-memory booking store between cases. */
export function __resetMockBookings(): void {
  bookings.clear()
  idempotency.clear()
}
