package com.digitalbluebird.bookings.adapter.inbound.web

import com.digitalbluebird.bookings.domain.Booking
import java.time.Instant

data class RequestBookingRequest(
    val idempotencyKey: String,
    val hideId: String,
    val observerId: String,
    val slotStart: Instant,
    val slotEnd: Instant,
    val partySize: Int,
)

data class TransitionBookingRequest(
    val expectedVersion: Long,
)

data class BookingResponse(
    val id: String,
    val hideId: String,
    val observerId: String,
    val slotStart: Instant,
    val slotEnd: Instant,
    val partySize: Int,
    val status: String,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(b: Booking) = BookingResponse(
            id = b.id.value.toString(),
            hideId = b.hideId.value.toString(),
            observerId = b.observerId.value.toString(),
            slotStart = b.slot.start,
            slotEnd = b.slot.endExclusive,
            partySize = b.partySize.value,
            status = b.status.name,
            version = b.version,
            createdAt = b.createdAt,
            updatedAt = b.updatedAt,
        )
    }
}

data class ErrorResponse(val code: String, val message: String)
