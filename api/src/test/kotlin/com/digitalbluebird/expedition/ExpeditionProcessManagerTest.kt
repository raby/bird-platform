package com.digitalbluebird.expedition

import com.digitalbluebird.bookings.domain.event.BookingCancelledEvent
import com.digitalbluebird.expeditions.domain.port.inbound.HandleBookingCancelledUseCase
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ExpeditionProcessManagerTest {

    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val handle = mockk<HandleBookingCancelledUseCase>(relaxed = true)
    private val processManager = ExpeditionProcessManager(handle, objectMapper)

    @Test
    fun `a BookingCancelled event forwards the booking id to the handler`() {
        val event = BookingCancelledEvent(
            bookingId = "bk-1",
            hideId = UUID.randomUUID().toString(),
            observerId = UUID.randomUUID().toString(),
            occurredAt = Instant.parse("2026-05-01T09:00:00Z"),
        )
        val entry = OutboxEntry(
            id = UUID.randomUUID(),
            aggregateType = BookingCancelledEvent.AGGREGATE_TYPE,
            aggregateId = "bk-1",
            eventType = BookingCancelledEvent.EVENT_TYPE,
            payload = objectMapper.writeValueAsString(event),
            occurredAt = event.occurredAt,
        )

        processManager.handle(entry)

        verify(exactly = 1) { handle.onBookingCancelled("bk-1") }
    }

    @Test
    fun `an unrelated event type is ignored`() {
        val entry = OutboxEntry(
            id = UUID.randomUUID(),
            aggregateType = "Booking",
            aggregateId = "x",
            eventType = "BookingConfirmed",
            payload = "{}",
            occurredAt = Instant.parse("2026-05-01T09:00:00Z"),
        )

        processManager.handle(entry)

        verify(exactly = 0) { handle.onBookingCancelled(any()) }
    }
}
