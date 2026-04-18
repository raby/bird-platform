package com.digitalbluebird.bookings.domain.event

import java.time.Instant

data class BookingRequestedEvent(
    val bookingId: String,
    val hideId: String,
    val observerId: String,
    val slotStart: Instant,
    val slotEnd: Instant,
    val partySize: Int,
    val occurredAt: Instant,
) {
    companion object {
        const val AGGREGATE_TYPE = "Booking"
        const val EVENT_TYPE = "BookingRequested"
    }
}

data class BookingConfirmedEvent(
    val bookingId: String,
    val hideId: String,
    val observerId: String,
    val occurredAt: Instant,
) {
    companion object {
        const val AGGREGATE_TYPE = "Booking"
        const val EVENT_TYPE = "BookingConfirmed"
    }
}

data class BookingCancelledEvent(
    val bookingId: String,
    val hideId: String,
    val observerId: String,
    val occurredAt: Instant,
) {
    companion object {
        const val AGGREGATE_TYPE = "Booking"
        const val EVENT_TYPE = "BookingCancelled"
    }
}
