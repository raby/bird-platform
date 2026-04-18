package com.digitalbluebird.bookings.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.bookings.domain.Booking
import com.digitalbluebird.bookings.domain.BookingError
import com.digitalbluebird.bookings.domain.BookingId

fun interface FindBookingUseCase {
    fun findById(id: BookingId): Either<BookingError, Booking>
}
