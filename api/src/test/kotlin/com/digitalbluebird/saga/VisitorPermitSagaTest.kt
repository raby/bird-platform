package com.digitalbluebird.saga

import arrow.core.right
import com.digitalbluebird.bookings.domain.Booking
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.BookingStatus
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.PartySize
import com.digitalbluebird.bookings.domain.event.BookingConfirmedEvent
import com.digitalbluebird.bookings.domain.event.BookingRequestedEvent
import com.digitalbluebird.bookings.domain.port.inbound.CancelBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.FindBookingUseCase
import com.digitalbluebird.permits.domain.PermitDecision
import com.digitalbluebird.permits.domain.port.inbound.IssueVisitorPermitUseCase
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class VisitorPermitSagaTest {

    private val issueVisitorPermit: IssueVisitorPermitUseCase = mockk()
    private val findBooking: FindBookingUseCase = mockk()
    private val cancelBooking: CancelBookingUseCase = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val saga = VisitorPermitSaga(issueVisitorPermit, findBooking, cancelBooking, objectMapper)

    private val bookingId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val hideId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val slotStart: Instant = Instant.parse("2026-05-02T08:00:00Z")
    private val slotEnd: Instant = Instant.parse("2026-05-02T10:00:00Z")
    private val day: LocalDate = LocalDate.of(2026, 5, 2)

    private fun confirmedEntry(partySize: Int = 2): OutboxEntry {
        val event = BookingConfirmedEvent(
            bookingId = bookingId.toString(),
            hideId = hideId.toString(),
            observerId = UUID.randomUUID().toString(),
            slotStart = slotStart,
            slotEnd = slotEnd,
            partySize = partySize,
            occurredAt = slotStart,
        )
        return OutboxEntry(
            id = UUID.randomUUID(),
            aggregateType = BookingConfirmedEvent.AGGREGATE_TYPE,
            aggregateId = bookingId.toString(),
            eventType = BookingConfirmedEvent.EVENT_TYPE,
            payload = objectMapper.writeValueAsString(event),
            occurredAt = slotStart,
        )
    }

    private fun booking(status: BookingStatus, version: Long) = Booking(
        id = BookingId(bookingId),
        hideId = HideId(hideId),
        observerId = ObserverId(UUID.randomUUID()),
        slot = InstantRange(slotStart, slotEnd),
        partySize = PartySize(2),
        status = status,
        version = version,
        createdAt = slotStart,
        updatedAt = slotStart,
    )

    @Test
    fun `an issued permit leaves the booking alone`() {
        every { issueVisitorPermit.issueFor(bookingId, hideId, day, 2) } returns PermitDecision.Issued

        saga.handle(confirmedEntry())

        verify(exactly = 0) { findBooking.findById(any()) }
        verify(exactly = 0) { cancelBooking.cancel(any(), any()) }
    }

    @Test
    fun `an exhausted cap cancels the confirmed booking at its current version`() {
        val confirmed = booking(BookingStatus.CONFIRMED, version = 1)
        every { issueVisitorPermit.issueFor(bookingId, hideId, day, 4) } returns PermitDecision.Exhausted
        every { findBooking.findById(BookingId(bookingId)) } returns confirmed.right()
        every { cancelBooking.cancel(BookingId(bookingId), 1) } returns confirmed.right()

        saga.handle(confirmedEntry(partySize = 4))

        verify(exactly = 1) { cancelBooking.cancel(BookingId(bookingId), 1) }
    }

    @Test
    fun `an exhausted cap is a no-op when the booking is already cancelled`() {
        every { issueVisitorPermit.issueFor(bookingId, hideId, day, 4) } returns PermitDecision.Exhausted
        every { findBooking.findById(BookingId(bookingId)) } returns booking(BookingStatus.CANCELLED, version = 2).right()

        saga.handle(confirmedEntry(partySize = 4))

        verify(exactly = 0) { cancelBooking.cancel(any(), any()) }
    }

    @Test
    fun `ignores events other than BookingConfirmed`() {
        val requested = BookingRequestedEvent(
            bookingId = bookingId.toString(),
            hideId = hideId.toString(),
            observerId = UUID.randomUUID().toString(),
            slotStart = slotStart,
            slotEnd = slotEnd,
            partySize = 2,
            occurredAt = slotStart,
        )
        val entry = OutboxEntry(
            id = UUID.randomUUID(),
            aggregateType = BookingRequestedEvent.AGGREGATE_TYPE,
            aggregateId = bookingId.toString(),
            eventType = BookingRequestedEvent.EVENT_TYPE,
            payload = objectMapper.writeValueAsString(requested),
            occurredAt = slotStart,
        )

        saga.handle(entry)

        verify(exactly = 0) { issueVisitorPermit.issueFor(any(), any(), any(), any()) }
    }

    // Guards that the day passed to the permit use-case is the slot's UTC calendar day.
    @Test
    fun `derives the permit day from the slot start in UTC`() {
        every { issueVisitorPermit.issueFor(bookingId, hideId, day, 2) } returns PermitDecision.Issued

        saga.handle(confirmedEntry())

        verify(exactly = 1) { issueVisitorPermit.issueFor(bookingId, hideId, LocalDate.of(2026, 5, 2), 2) }
    }
}
