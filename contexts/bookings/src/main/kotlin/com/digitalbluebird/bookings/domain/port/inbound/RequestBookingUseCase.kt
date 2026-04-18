package com.digitalbluebird.bookings.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.bookings.domain.Booking
import com.digitalbluebird.bookings.domain.BookingError
import java.time.Instant

data class RequestBookingCommand(
    val idempotencyKey: String,
    val hideId: String,
    val observerId: String,
    val slotStart: Instant,
    val slotEnd: Instant,
    val partySize: Int,
)

fun interface RequestBookingUseCase {
    fun request(command: RequestBookingCommand): Either<BookingError, Booking>
}
