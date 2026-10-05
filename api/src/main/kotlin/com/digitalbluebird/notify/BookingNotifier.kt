package com.digitalbluebird.notify

import com.digitalbluebird.bookings.domain.event.BookingCancelledEvent
import com.digitalbluebird.bookings.domain.event.BookingConfirmedEvent
import com.digitalbluebird.notifications.domain.port.inbound.NotificationRequest
import com.digitalbluebird.notifications.domain.port.inbound.NotifyUseCase
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.digitalbluebird.shared.infra.outbox.OutboxHandler
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Turns booking lifecycle events into notifications. It runs as an [OutboxHandler] in the shared
 * relay's transaction, alongside the hide-availability projector and the visitor-permit saga, and
 * calls the notifications context through its [NotifyUseCase] port — translating a bookings event into
 * a generic notification request. The notifications context knows nothing about bookings; this bridge
 * lives in the api composition root, the one place allowed to depend on both.
 *
 * It passes the outbox entry's own id as the notification's source-event id, which the notifications
 * context deduplicates on. Under the current in-process relay that drain is one transaction, so a
 * committed entry is already delivered once and the dedup rarely fires; it is the mechanism that keeps
 * consumption correct the moment the same event can arrive more than once — a non-transactional relay,
 * or a real broker — which is the whole point of the inbox pattern.
 */
class BookingNotifier(
    private val notify: NotifyUseCase,
    private val objectMapper: ObjectMapper,
) : OutboxHandler {

    override fun handle(entry: OutboxEntry) {
        when (entry.eventType) {
            BookingConfirmedEvent.EVENT_TYPE -> {
                val event = objectMapper.readValue(entry.payload, BookingConfirmedEvent::class.java)
                notify.notify(
                    NotificationRequest(
                        kind = "BOOKING_CONFIRMED",
                        recipient = event.observerId,
                        subject = "Your hide booking is confirmed",
                        body = "Booking ${event.bookingId} at hide ${event.hideId} for ${event.slotStart} to " +
                            "${event.slotEnd} (party of ${event.partySize}) is confirmed.",
                        sourceEventId = entry.id,
                    ),
                )
            }

            BookingCancelledEvent.EVENT_TYPE -> {
                val event = objectMapper.readValue(entry.payload, BookingCancelledEvent::class.java)
                notify.notify(
                    NotificationRequest(
                        kind = "BOOKING_CANCELLED",
                        recipient = event.observerId,
                        subject = "Your hide booking was cancelled",
                        body = "Booking ${event.bookingId} at hide ${event.hideId} has been cancelled.",
                        sourceEventId = entry.id,
                    ),
                )
            }

            else -> Unit
        }
    }
}
