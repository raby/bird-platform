package com.digitalbluebird.bookings.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.PartySize
import com.digitalbluebird.bookings.domain.event.BookingCancelledEvent
import com.digitalbluebird.bookings.domain.event.BookingConfirmedEvent
import com.digitalbluebird.bookings.domain.event.BookingRequestedEvent
import com.digitalbluebird.bookings.domain.port.outbound.ConfirmedStay
import com.digitalbluebird.bookings.domain.port.outbound.HideAvailabilityReadModel
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class HideAvailabilityProjectorTest {

    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val readModel: HideAvailabilityReadModel = mockk(relaxed = true)
    private val projector = HideAvailabilityProjector(readModel, objectMapper)

    private val bookingId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val hideId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val observerId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val occurredAt: Instant = Instant.parse("2026-06-01T09:00:00Z")
    private val slotStart: Instant = Instant.parse("2026-06-02T08:00:00Z")
    private val slotEnd: Instant = Instant.parse("2026-06-02T10:00:00Z")

    private fun entry(eventType: String, payload: Any) = OutboxEntry(
        id = UUID.randomUUID(),
        aggregateType = "Booking",
        aggregateId = bookingId.toString(),
        eventType = eventType,
        payload = objectMapper.writeValueAsString(payload),
        occurredAt = occurredAt,
    )

    @Test
    fun `projects a confirmed event into occupancy`() {
        val event = BookingConfirmedEvent(
            bookingId = bookingId.toString(),
            hideId = hideId.toString(),
            observerId = observerId.toString(),
            slotStart = slotStart,
            slotEnd = slotEnd,
            partySize = 3,
            occurredAt = occurredAt,
        )
        val stay = slot<ConfirmedStay>()
        every { readModel.applyConfirmed(capture(stay)) } just Runs

        projector.handle(entry(BookingConfirmedEvent.EVENT_TYPE, event))

        verify(exactly = 1) { readModel.applyConfirmed(any()) }
        assertThat(stay.captured.bookingId).isEqualTo(BookingId(bookingId))
        assertThat(stay.captured.hideId).isEqualTo(HideId(hideId))
        assertThat(stay.captured.observerId).isEqualTo(ObserverId(observerId))
        assertThat(stay.captured.slot).isEqualTo(InstantRange(slotStart, slotEnd))
        assertThat(stay.captured.partySize).isEqualTo(PartySize(3))
        assertThat(stay.captured.confirmedAt).isEqualTo(occurredAt)
    }

    @Test
    fun `removes occupancy on a cancelled event`() {
        val event = BookingCancelledEvent(
            bookingId = bookingId.toString(),
            hideId = hideId.toString(),
            observerId = observerId.toString(),
            occurredAt = occurredAt,
        )

        projector.handle(entry(BookingCancelledEvent.EVENT_TYPE, event))

        verify(exactly = 1) { readModel.removeByBooking(BookingId(bookingId)) }
    }

    @Test
    fun `ignores unrelated event types`() {
        val event = BookingRequestedEvent(
            bookingId = bookingId.toString(),
            hideId = hideId.toString(),
            observerId = observerId.toString(),
            slotStart = slotStart,
            slotEnd = slotEnd,
            partySize = 2,
            occurredAt = occurredAt,
        )

        projector.handle(entry(BookingRequestedEvent.EVENT_TYPE, event))

        verify(exactly = 0) { readModel.applyConfirmed(any()) }
        verify(exactly = 0) { readModel.removeByBooking(any()) }
    }
}
