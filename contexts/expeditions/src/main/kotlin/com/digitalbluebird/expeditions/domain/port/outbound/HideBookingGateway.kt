package com.digitalbluebird.expeditions.domain.port.outbound

import java.time.Instant

/**
 * How the expedition process manager books and releases hides, in the expedition's own terms. The
 * implementation lives in the api composition root and adapts these to the bookings context, so the
 * expeditions context stays independent of it.
 */
interface HideBookingGateway {
    /** Book one hide for the party; returns the booking id, or Unavailable if the slot could not be held. */
    fun book(hideId: String, observerId: String, slotStart: Instant, slotEnd: Instant, partySize: Int): BookingResult

    /** Release a booking this expedition made. Idempotent: a no-op if it is already cancelled. */
    fun cancel(bookingId: String)
}

sealed interface BookingResult {
    data class Booked(val bookingId: String) : BookingResult
    data object Unavailable : BookingResult
}
