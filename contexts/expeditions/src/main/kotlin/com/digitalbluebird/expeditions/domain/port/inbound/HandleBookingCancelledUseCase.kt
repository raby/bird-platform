package com.digitalbluebird.expeditions.domain.port.inbound

/**
 * The process manager's async entry point: told that a booking was cancelled (e.g. the visitor-permit
 * saga cancelled an over-cap expedition booking), it finds the owning expedition and, if the outing was
 * still in play, compensates — releasing its other hides and failing the whole expedition.
 *
 * Idempotent: a cancellation for an already-failing expedition — including the ones this compensation
 * itself triggers — just records that leg, so there is no compensation loop. A booking id that belongs
 * to no expedition is ignored.
 */
fun interface HandleBookingCancelledUseCase {
    fun onBookingCancelled(bookingId: String)
}
