package com.digitalbluebird.bookings.domain

import java.util.UUID

@JvmInline
value class BookingId(val value: UUID) {
    companion object {
        fun random(): BookingId = BookingId(UUID.randomUUID())
    }
}
