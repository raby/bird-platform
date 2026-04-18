package com.digitalbluebird.bookings.domain.port.outbound

import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.IdempotencyKey

interface IdempotencyKeysRepository {
    /**
     * Atomically insert a mapping from [key] to [bookingId].
     * Returns true if the insert succeeded (first use); false if the key already exists.
     */
    fun register(key: IdempotencyKey, bookingId: BookingId): Boolean

    fun findBookingId(key: IdempotencyKey): BookingId?
}
