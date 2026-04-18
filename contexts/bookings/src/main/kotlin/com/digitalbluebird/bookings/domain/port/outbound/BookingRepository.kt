package com.digitalbluebird.bookings.domain.port.outbound

import com.digitalbluebird.bookings.domain.Booking
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.shared.domain.InstantRange

interface BookingRepository {
    fun insert(booking: Booking): Booking
    fun findById(id: BookingId): Booking?

    /**
     * Conditional update protected by optimistic locking.
     * Returns the updated booking (with incremented version) iff the stored row still has [expectedVersion];
     * otherwise returns null to signal a lost update.
     */
    fun updateIfVersionMatches(booking: Booking, expectedVersion: Long): Booking?

    /**
     * True when the hide has a non-cancelled booking whose slot overlaps the requested range.
     */
    fun hasOverlappingActiveBooking(hideId: HideId, slot: InstantRange): Boolean
}
