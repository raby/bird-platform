package com.digitalbluebird.notify

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.bookings.domain.event.BookingCancelledEvent
import com.digitalbluebird.bookings.domain.event.BookingConfirmedEvent
import com.digitalbluebird.notifications.domain.port.inbound.NotificationRequest
import com.digitalbluebird.notifications.domain.port.inbound.NotifyUseCase
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class BookingNotifierTest {

    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val notify = mockk<NotifyUseCase>(relaxed = true)
    private val notifier = BookingNotifier(notify, objectMapper)

    private fun entry(eventType: String, payload: String) = OutboxEntry(
        id = UUID.randomUUID(),
        aggregateType = "Booking",
        aggregateId = UUID.randomUUID().toString(),
        eventType = eventType,
        payload = payload,
        occurredAt = Instant.parse("2026-05-01T09:00:00Z"),
    )

    @Test
    fun `a confirmed booking becomes a BOOKING_CONFIRMED notification keyed on the entry id`() {
        val confirmed = BookingConfirmedEvent(
            bookingId = UUID.randomUUID().toString(),
            hideId = UUID.randomUUID().toString(),
            observerId = "observer-1",
            slotStart = Instant.parse("2026-05-02T08:00:00Z"),
            slotEnd = Instant.parse("2026-05-02T10:00:00Z"),
            partySize = 2,
            occurredAt = Instant.parse("2026-05-01T09:00:00Z"),
        )
        val e = entry(BookingConfirmedEvent.EVENT_TYPE, objectMapper.writeValueAsString(confirmed))
        val req = slot<NotificationRequest>()
        every { notify.notify(capture(req)) } returns Unit

        notifier.handle(e)

        assertThat(req.captured.kind).isEqualTo("BOOKING_CONFIRMED")
        assertThat(req.captured.recipient).isEqualTo("observer-1")
        assertThat(req.captured.sourceEventId).isEqualTo(e.id)
    }

    @Test
    fun `a cancelled booking becomes a BOOKING_CANCELLED notification`() {
        val cancelled = BookingCancelledEvent(
            bookingId = UUID.randomUUID().toString(),
            hideId = UUID.randomUUID().toString(),
            observerId = "observer-9",
            occurredAt = Instant.parse("2026-05-01T09:00:00Z"),
        )
        val e = entry(BookingCancelledEvent.EVENT_TYPE, objectMapper.writeValueAsString(cancelled))
        val req = slot<NotificationRequest>()
        every { notify.notify(capture(req)) } returns Unit

        notifier.handle(e)

        assertThat(req.captured.kind).isEqualTo("BOOKING_CANCELLED")
        assertThat(req.captured.recipient).isEqualTo("observer-9")
        assertThat(req.captured.sourceEventId).isEqualTo(e.id)
    }

    @Test
    fun `an unrelated event type is ignored`() {
        notifier.handle(entry("BookingRequested", "{}"))
        verify(exactly = 0) { notify.notify(any()) }
    }
}
