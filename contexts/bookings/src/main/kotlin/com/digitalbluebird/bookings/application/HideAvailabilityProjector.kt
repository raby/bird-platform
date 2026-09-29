package com.digitalbluebird.bookings.application

import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.PartySize
import com.digitalbluebird.bookings.domain.event.BookingCancelledEvent
import com.digitalbluebird.bookings.domain.event.BookingConfirmedEvent
import com.digitalbluebird.bookings.domain.port.outbound.ConfirmedStay
import com.digitalbluebird.bookings.domain.port.outbound.HideAvailabilityReadModel
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.digitalbluebird.shared.infra.outbox.OutboxHandler
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * Projects booking lifecycle events into the hide-availability read model: a confirmed booking adds
 * occupancy, a cancellation frees it (a no-op if it was never confirmed), and other event types are
 * ignored. Both projection ops are idempotent, so re-delivery by the at-least-once outbox relay is safe.
 */
class HideAvailabilityProjector(
    private val readModel: HideAvailabilityReadModel,
    private val objectMapper: ObjectMapper,
) : OutboxHandler {

    override fun handle(entry: OutboxEntry) {
        when (entry.eventType) {
            BookingConfirmedEvent.EVENT_TYPE -> {
                val event = objectMapper.readValue(entry.payload, BookingConfirmedEvent::class.java)
                readModel.applyConfirmed(
                    ConfirmedStay(
                        bookingId = BookingId(UUID.fromString(event.bookingId)),
                        hideId = HideId(UUID.fromString(event.hideId)),
                        observerId = ObserverId.fromString(event.observerId),
                        slot = InstantRange(event.slotStart, event.slotEnd),
                        partySize = PartySize(event.partySize),
                        confirmedAt = event.occurredAt,
                    ),
                )
            }

            BookingCancelledEvent.EVENT_TYPE -> {
                val event = objectMapper.readValue(entry.payload, BookingCancelledEvent::class.java)
                readModel.removeByBooking(BookingId(UUID.fromString(event.bookingId)))
            }

            else -> Unit
        }
    }
}
