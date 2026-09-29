import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CreateBookingRequest } from '../src/api'
import {
  __resetMockBookings,
  mockCancelBooking,
  mockConfirmBooking,
  mockCreateBooking,
  mockGetAvailability,
  mockListHides,
} from '../src/mock'

const KINGFISHER = '10000000-0000-0000-0000-000000000001'
const OBSERVER = '22222222-2222-2222-2222-222222222222'
const SLOT_START = '2026-06-02T08:00:00.000Z'
const SLOT_END = '2026-06-02T10:00:00.000Z'

const req = (over: Partial<CreateBookingRequest> = {}): CreateBookingRequest => ({
  idempotencyKey: `key-${Math.random().toString(36).slice(2, 12)}`,
  hideId: KINGFISHER,
  observerId: OBSERVER,
  slotStart: SLOT_START,
  slotEnd: SLOT_END,
  partySize: 2,
  ...over,
})

// The mock resolves after an internal setTimeout; advance fake timers past it.
async function settle<T>(p: Promise<T>): Promise<T> {
  await vi.advanceTimersByTimeAsync(200)
  return p
}

describe('mock booking store (mirrors the backend concurrency semantics)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    __resetMockBookings()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('lists the four seeded hides', async () => {
    const hides = await settle(mockListHides())
    expect(hides).toHaveLength(4)
    expect(hides.map((h) => h.name)).toContain('Kingfisher Hide')
  })

  it('creates a REQUESTED booking at version 0', async () => {
    const res = await settle(mockCreateBooking(req()))
    expect(res.ok).toBe(true)
    if (res.ok) {
      expect(res.booking.status).toBe('REQUESTED')
      expect(res.booking.version).toBe(0)
    }
  })

  it('rejects an idempotency key that is too short', async () => {
    const res = await settle(mockCreateBooking(req({ idempotencyKey: 'short' })))
    expect(res.ok).toBe(false)
    if (!res.ok) expect(res.error.code).toBe('InvalidIdempotencyKey')
  })

  it('is idempotent — the same key returns the same booking', async () => {
    const first = await settle(mockCreateBooking(req({ idempotencyKey: 'idem-same-key' })))
    const second = await settle(mockCreateBooking(req({ idempotencyKey: 'idem-same-key' })))
    expect(first.ok && second.ok).toBe(true)
    if (first.ok && second.ok) expect(second.booking.id).toBe(first.booking.id)
  })

  it('rejects an overlapping active booking on the same hide slot', async () => {
    await settle(mockCreateBooking(req({ idempotencyKey: 'first-slot-key' })))
    const res = await settle(mockCreateBooking(req({ idempotencyKey: 'overlap-slot-key' })))
    expect(res.ok).toBe(false)
    if (!res.ok) expect(res.error.code).toBe('HideSlotUnavailable')
  })

  it('confirm bumps the version; a second confirm with the stale version conflicts (the CAS)', async () => {
    const created = await settle(mockCreateBooking(req()))
    if (!created.ok) throw new Error('setup failed')
    const id = created.booking.id

    // Two callers both hold version 0 — exactly the race the platform is built to survive.
    const winner = await settle(mockConfirmBooking(id, 0))
    const loser = await settle(mockConfirmBooking(id, 0))

    expect(winner.ok).toBe(true)
    if (winner.ok) expect(winner.booking.version).toBe(1)
    expect(loser.ok).toBe(false)
    if (!loser.ok) expect(loser.error.code).toBe('VersionConflict')
  })

  it('cannot confirm a cancelled booking (state machine)', async () => {
    const created = await settle(mockCreateBooking(req()))
    if (!created.ok) throw new Error('setup failed')
    const id = created.booking.id

    await settle(mockCancelBooking(id, 0))
    const res = await settle(mockConfirmBooking(id, 1))

    expect(res.ok).toBe(false)
    if (!res.ok) expect(res.error.code).toBe('StateTransitionNotAllowed')
  })

  it('availability counts only confirmed occupancy', async () => {
    const created = await settle(mockCreateBooking(req({ partySize: 4 })))
    if (!created.ok) throw new Error('setup failed')

    // Requested but not confirmed → nothing occupied yet.
    let a = await settle(mockGetAvailability(KINGFISHER, SLOT_START, SLOT_END))
    expect(a.occupied).toBe(0)
    expect(a.seatsLeft).toBe(6)

    await settle(mockConfirmBooking(created.booking.id, 0))
    a = await settle(mockGetAvailability(KINGFISHER, SLOT_START, SLOT_END))
    expect(a.occupied).toBe(4)
    expect(a.seatsLeft).toBe(2)
  })
})
