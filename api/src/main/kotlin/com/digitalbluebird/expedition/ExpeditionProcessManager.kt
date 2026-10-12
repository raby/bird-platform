package com.digitalbluebird.expedition

import com.digitalbluebird.bookings.domain.event.BookingCancelledEvent
import com.digitalbluebird.expeditions.domain.port.inbound.HandleBookingCancelledUseCase
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.digitalbluebird.shared.infra.outbox.OutboxHandler
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Drives the async half of the expedition process manager. As an [OutboxHandler] it runs in the shared
 * relay's transaction (beside the projector, saga, notifier and eBird sync); on a BookingCancelled it
 * forwards the booking id to the expeditions context, which decides whether that booking belonged to an
 * expedition and, if so, compensates. The context owns the correlation and the loop-avoidance; this
 * bridge only translates the event.
 */
class ExpeditionProcessManager(
    private val handleBookingCancelled: HandleBookingCancelledUseCase,
    private val objectMapper: ObjectMapper,
) : OutboxHandler {

    override fun handle(entry: OutboxEntry) {
        if (entry.eventType != BookingCancelledEvent.EVENT_TYPE) return
        val event = objectMapper.readValue(entry.payload, BookingCancelledEvent::class.java)
        handleBookingCancelled.onBookingCancelled(event.bookingId)
    }
}
