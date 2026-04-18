package com.digitalbluebird.bookings.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
import java.time.Instant

data class Booking(
    val id: BookingId,
    val hideId: HideId,
    val observerId: ObserverId,
    val slot: InstantRange,
    val partySize: PartySize,
    val status: BookingStatus,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun confirm(now: Instant): Either<BookingError, Booking> =
        transition(BookingStatus.CONFIRMED, now)

    fun cancel(now: Instant): Either<BookingError, Booking> =
        transition(BookingStatus.CANCELLED, now)

    private fun transition(next: BookingStatus, now: Instant): Either<BookingError, Booking> =
        if (status.canTransitionTo(next)) {
            copy(status = next, updatedAt = now).right()
        } else {
            BookingError.StateTransitionNotAllowed(id, status, next).left()
        }

    companion object {
        fun request(
            id: BookingId,
            hideId: HideId,
            observerId: ObserverId,
            slot: InstantRange,
            partySize: PartySize,
            now: Instant,
        ): Booking = Booking(
            id = id,
            hideId = hideId,
            observerId = observerId,
            slot = slot,
            partySize = partySize,
            status = BookingStatus.REQUESTED,
            version = 0L,
            createdAt = now,
            updatedAt = now,
        )
    }
}
