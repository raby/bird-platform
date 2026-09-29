// Typed client for the bird-platform REST API. The types mirror the Kotlin DTOs exactly
// (species + bookings adapter/inbound/web/*Dtos.kt). With VITE_API_BASE set it calls the real
// controllers; unset, it falls back to the in-memory store (./mock) so the SPA runs with no backend.
import {
  mockCancelBooking,
  mockConfirmBooking,
  mockCreateBooking,
  mockGetAvailability,
  mockListHides,
  mockSearchSpecies,
} from './mock'

/** Mirrors `SpeciesResponse` (Kotlin). `conservationStatus` is an IUCN enum name, e.g. `VULNERABLE`. */
export interface SpeciesResponse {
  readonly id: string
  readonly scientificName: string
  readonly commonNames: readonly CommonName[]
  readonly taxonomy: Taxonomy
  readonly conservationStatus: string
}

export interface CommonName {
  readonly language: string
  readonly name: string
}

export interface Taxonomy {
  readonly kingdom: string
  readonly phylum: string
  readonly className: string
  readonly order: string
  readonly family: string
  readonly genus: string
}

/** Mirrors `ErrorResponse` (Kotlin): `{ code, message }`. */
export interface ErrorResponse {
  readonly code: string
  readonly message: string
}

export type SearchResult =
  | { readonly ok: true; readonly species: readonly SpeciesResponse[] }
  | { readonly ok: false; readonly error: ErrorResponse }

/** Mirrors `HideResponse` (Kotlin). */
export interface HideResponse {
  readonly id: string
  readonly name: string
  readonly reserve: string
  readonly capacity: number
}

/** Mirrors `HideAvailabilityResponse` (Kotlin). */
export interface HideAvailabilityResponse {
  readonly hideId: string
  readonly name: string
  readonly reserve: string
  readonly capacity: number
  readonly occupied: number
  readonly seatsLeft: number
  readonly from: string
  readonly to: string
}

export type BookingStatus = 'REQUESTED' | 'CONFIRMED' | 'CANCELLED'

/** Mirrors `BookingResponse` (Kotlin), incl. the `version` that drives the optimistic lock. */
export interface BookingResponse {
  readonly id: string
  readonly hideId: string
  readonly observerId: string
  readonly slotStart: string
  readonly slotEnd: string
  readonly partySize: number
  readonly status: BookingStatus
  readonly version: number
  readonly createdAt: string
  readonly updatedAt: string
}

/** Mirrors `RequestBookingRequest` (Kotlin). */
export interface CreateBookingRequest {
  readonly idempotencyKey: string
  readonly hideId: string
  readonly observerId: string
  readonly slotStart: string
  readonly slotEnd: string
  readonly partySize: number
}

export type BookingResult =
  | { readonly ok: true; readonly booking: BookingResponse }
  | { readonly ok: false; readonly error: ErrorResponse }

const API_BASE = import.meta.env.VITE_API_BASE
const useMock = API_BASE === undefined || API_BASE === ''

/**
 * `GET /species/search?q=&limit=` → `SpeciesResponse[]`, or an `ErrorResponse` on 4xx/5xx
 * (e.g. `InvalidQuery` when the query is under 2 characters, matching the backend rule).
 */
export async function searchSpecies(
  query: string,
  limit = 20,
  abort?: AbortSignal,
): Promise<SearchResult> {
  if (useMock) return mockSearchSpecies(query, limit)
  const url = `${API_BASE}/species/search?q=${encodeURIComponent(query)}&limit=${String(limit)}`
  const res = await fetch(url, abort ? { signal: abort } : {})
  if (res.ok) {
    return { ok: true, species: (await res.json()) as SpeciesResponse[] }
  }
  return { ok: false, error: await errorFrom(res) }
}

/** `GET /hides` → all hides. */
export async function listHides(): Promise<readonly HideResponse[]> {
  if (useMock) return mockListHides()
  const res = await fetch(`${API_BASE}/hides`)
  if (!res.ok) throw new Error(`GET /hides failed (HTTP ${String(res.status)})`)
  return (await res.json()) as HideResponse[]
}

/** `GET /hides/{id}/availability?from=&to=` → occupancy + seats-left for the slot. */
export async function getAvailability(
  hideId: string,
  from: string,
  to: string,
  abort?: AbortSignal,
): Promise<HideAvailabilityResponse> {
  if (useMock) return mockGetAvailability(hideId, from, to)
  const url =
    `${API_BASE}/hides/${encodeURIComponent(hideId)}/availability` +
    `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
  const res = await fetch(url, abort ? { signal: abort } : {})
  if (!res.ok) throw new Error(`GET availability failed (HTTP ${String(res.status)})`)
  return (await res.json()) as HideAvailabilityResponse
}

/** `POST /bookings` → 201 with the REQUESTED booking, or a domain error (e.g. `HideSlotUnavailable`). */
export async function createBooking(request: CreateBookingRequest): Promise<BookingResult> {
  if (useMock) return mockCreateBooking(request)
  return postBooking(`${API_BASE}/bookings`, request, 201)
}

/** `POST /bookings/{id}/confirm` with `expectedVersion` → 200, or `409 VersionConflict` if stale. */
export async function confirmBooking(id: string, expectedVersion: number): Promise<BookingResult> {
  if (useMock) return mockConfirmBooking(id, expectedVersion)
  return postBooking(`${API_BASE}/bookings/${encodeURIComponent(id)}/confirm`, { expectedVersion }, 200)
}

/** `POST /bookings/{id}/cancel` with `expectedVersion` → 200, or `409 VersionConflict` if stale. */
export async function cancelBooking(id: string, expectedVersion: number): Promise<BookingResult> {
  if (useMock) return mockCancelBooking(id, expectedVersion)
  return postBooking(`${API_BASE}/bookings/${encodeURIComponent(id)}/cancel`, { expectedVersion }, 200)
}

async function postBooking(url: string, body: unknown, okStatus: number): Promise<BookingResult> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (res.status === okStatus) {
    return { ok: true, booking: (await res.json()) as BookingResponse }
  }
  return { ok: false, error: await errorFrom(res) }
}

async function errorFrom(res: Response): Promise<ErrorResponse> {
  return (await res.json().catch(() => ({
    code: 'ERROR',
    message: `Request failed (HTTP ${String(res.status)})`,
  }))) as ErrorResponse
}
