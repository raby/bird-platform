package com.digitalbluebird.bookings.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.digitalbluebird.bookings.adapter.outbound.persistence.OverlappingSlotException
import com.digitalbluebird.bookings.domain.Booking
import com.digitalbluebird.bookings.domain.BookingError
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.BookingStatus
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.IdempotencyKey
import com.digitalbluebird.bookings.domain.PartySize
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingCommand
import com.digitalbluebird.bookings.domain.port.outbound.BookingRepository
import com.digitalbluebird.bookings.domain.port.outbound.IdempotencyKeysRepository
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.digitalbluebird.shared.infra.outbox.OutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class BookingServiceTest {

    private val fixedInstant: Instant = Instant.parse("2026-04-18T12:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val bookings: BookingRepository = mockk(relaxed = true)
    private val idempotencyKeys: IdempotencyKeysRepository = mockk(relaxed = true)
    private val outbox: OutboxRepository = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val service = BookingService(bookings, idempotencyKeys, outbox, clock, objectMapper)

    private val hideId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val observerId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val slotStart = fixedInstant.plusSeconds(3600)
    private val slotEnd = fixedInstant.plusSeconds(7200)

    private fun validCommand(key: String = "idem-abcdef01") = RequestBookingCommand(
        idempotencyKey = key,
        hideId = hideId.toString(),
        observerId = observerId.toString(),
        slotStart = slotStart,
        slotEnd = slotEnd,
        partySize = 2,
    )

    private fun bookingFixture(
        id: BookingId = BookingId.random(),
        status: BookingStatus = BookingStatus.REQUESTED,
        version: Long = 0,
    ) = Booking(
        id = id,
        hideId = HideId(hideId),
        observerId = ObserverId(observerId),
        slot = InstantRange(slotStart, slotEnd),
        partySize = PartySize(2),
        status = status,
        version = version,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
    )

    @Test
    fun `request persists booking, registers idempotency key, and writes outbox`() {
        every { idempotencyKeys.findBookingId(any()) } returns null
        every { idempotencyKeys.register(any(), any()) } returns true
        every { bookings.hasOverlappingActiveBooking(any(), any()) } returns false
        val inserted = slot<Booking>()
        every { bookings.insert(capture(inserted)) } answers { inserted.captured }
        val entry = slot<OutboxEntry>()
        every { outbox.save(capture(entry)) } answers { }

        val result = service.request(validCommand())

        assertThat(result.isRight()).isTrue()
        assertThat(result.getOrNull()!!.status).isEqualTo(BookingStatus.REQUESTED)
        assertThat(entry.captured.eventType).isEqualTo("BookingRequested")
        assertThat(entry.captured.aggregateType).isEqualTo("Booking")
        verify(exactly = 1) { idempotencyKeys.register(any(), any()) }
        verify(exactly = 1) { bookings.insert(any()) }
    }

    @Test
    fun `request returns existing booking when idempotency key was already used`() {
        val prior = bookingFixture(version = 1, status = BookingStatus.CONFIRMED)
        every { idempotencyKeys.findBookingId(IdempotencyKey("idem-abcdef01")) } returns prior.id
        every { bookings.findById(prior.id) } returns prior

        val result = service.request(validCommand())

        assertThat(result.getOrNull()!!).isEqualTo(prior)
        verify(exactly = 0) { bookings.insert(any()) }
        verify(exactly = 0) { idempotencyKeys.register(any(), any()) }
        verify(exactly = 0) { outbox.save(any()) }
    }

    @Test
    fun `request rejects invalid idempotency key`() {
        val result = service.request(validCommand(key = "short"))
        assertThat(result.leftOrNull()!!).isInstanceOf(BookingError.InvalidIdempotencyKey::class)
        verify(exactly = 0) { bookings.insert(any()) }
    }

    @Test
    fun `request rejects invalid hide id`() {
        every { idempotencyKeys.findBookingId(any()) } returns null
        val result = service.request(validCommand().copy(hideId = "not-a-uuid"))
        assertThat(result.leftOrNull()!!).isInstanceOf(BookingError.InvalidHideId::class)
    }

    @Test
    fun `request returns HideSlotUnavailable when overlap exists`() {
        every { idempotencyKeys.findBookingId(any()) } returns null
        every { bookings.hasOverlappingActiveBooking(any(), any()) } returns true

        val result = service.request(validCommand())

        assertThat(result.leftOrNull()!!).isInstanceOf(BookingError.HideSlotUnavailable::class)
        verify(exactly = 0) { bookings.insert(any()) }
        verify(exactly = 0) { idempotencyKeys.register(any(), any()) }
    }

    @Test
    fun `request propagates an exclusion-constraint conflict so the transaction rolls back`() {
        every { idempotencyKeys.findBookingId(any()) } returns null
        every { bookings.hasOverlappingActiveBooking(any(), any()) } returns false
        every { idempotencyKeys.register(any(), any()) } returns true
        // The slot passed the pre-check but the storage-layer exclusion constraint rejected the
        // insert (a race). The service must NOT swallow it: the failed INSERT aborted the
        // transaction, so it has to propagate to trigger the @Transactional rollback; the web layer
        // maps it to the same 409 HideSlotUnavailable the pre-check path returns.
        every { bookings.insert(any()) } throws OverlappingSlotException(HideId(hideId))

        assertThat(runCatching { service.request(validCommand()) })
            .isFailure().isInstanceOf(OverlappingSlotException::class)
        verify(exactly = 0) { outbox.save(any()) }
    }

    @Test
    fun `request handles race where another writer wins the idempotency key`() {
        val winner = bookingFixture()
        every { idempotencyKeys.findBookingId(any()) } returnsMany listOf(null, winner.id)
        every { bookings.hasOverlappingActiveBooking(any(), any()) } returns false
        every { idempotencyKeys.register(any(), any()) } returns false
        every { bookings.findById(winner.id) } returns winner

        val result = service.request(validCommand())

        assertThat(result.getOrNull()!!).isEqualTo(winner)
        verify(exactly = 0) { bookings.insert(any()) }
    }

    @Test
    fun `confirm transitions requested to confirmed and writes outbox`() {
        val existing = bookingFixture(version = 0)
        every { bookings.findById(existing.id) } returns existing
        every { bookings.updateIfVersionMatches(any(), 0) } answers {
            firstArg<Booking>().copy(version = 1)
        }
        val entry = slot<OutboxEntry>()
        every { outbox.save(capture(entry)) } answers { }

        val result = service.confirm(existing.id, expectedVersion = 0)

        val confirmed = result.getOrNull()!!
        assertThat(confirmed.status).isEqualTo(BookingStatus.CONFIRMED)
        assertThat(confirmed.version).isEqualTo(1L)
        assertThat(entry.captured.eventType).isEqualTo("BookingConfirmed")
    }

    @Test
    fun `confirm returns VersionConflict when expected version is stale`() {
        val existing = bookingFixture(version = 5)
        every { bookings.findById(existing.id) } returns existing

        val result = service.confirm(existing.id, expectedVersion = 4)

        val err = result.leftOrNull()!!
        assertThat(err).isInstanceOf(BookingError.VersionConflict::class)
        verify(exactly = 0) { bookings.updateIfVersionMatches(any(), any()) }
        verify(exactly = 0) { outbox.save(any()) }
    }

    @Test
    fun `confirm returns VersionConflict when update lost the race`() {
        val existing = bookingFixture(version = 0)
        every { bookings.findById(existing.id) } returns existing
        every { bookings.updateIfVersionMatches(any(), 0) } returns null

        val result = service.confirm(existing.id, expectedVersion = 0)

        assertThat(result.leftOrNull()!!).isInstanceOf(BookingError.VersionConflict::class)
        verify(exactly = 0) { outbox.save(any()) }
    }

    @Test
    fun `confirm returns StateTransitionNotAllowed when booking is cancelled`() {
        val cancelled = bookingFixture(status = BookingStatus.CANCELLED, version = 1)
        every { bookings.findById(cancelled.id) } returns cancelled

        val result = service.confirm(cancelled.id, expectedVersion = 1)

        assertThat(result.leftOrNull()!!).isInstanceOf(BookingError.StateTransitionNotAllowed::class)
        verify(exactly = 0) { bookings.updateIfVersionMatches(any(), any()) }
    }

    @Test
    fun `confirm returns BookingNotFound when id is unknown`() {
        val missing = BookingId.random()
        every { bookings.findById(missing) } returns null

        val result = service.confirm(missing, expectedVersion = 0)

        assertThat(result.leftOrNull()!!).isInstanceOf(BookingError.BookingNotFound::class)
    }

    @Test
    fun `cancel transitions confirmed to cancelled`() {
        val confirmed = bookingFixture(status = BookingStatus.CONFIRMED, version = 1)
        every { bookings.findById(confirmed.id) } returns confirmed
        every { bookings.updateIfVersionMatches(any(), 1) } answers {
            firstArg<Booking>().copy(version = 2)
        }
        val entry = slot<OutboxEntry>()
        every { outbox.save(capture(entry)) } answers { }

        val result = service.cancel(confirmed.id, expectedVersion = 1)

        val cancelled = result.getOrNull()!!
        assertThat(cancelled.status).isEqualTo(BookingStatus.CANCELLED)
        assertThat(cancelled.version).isEqualTo(2L)
        assertThat(entry.captured.eventType).isEqualTo("BookingCancelled")
    }

    @Test
    fun `findById returns the booking when present`() {
        val found = bookingFixture(version = 3)
        every { bookings.findById(found.id) } returns found

        val result = service.findById(found.id)

        assertThat(result.getOrNull()!!).isEqualTo(found)
    }

    @Test
    fun `findById returns BookingNotFound when absent`() {
        val id = BookingId.random()
        every { bookings.findById(id) } returns null
        assertThat(service.findById(id).leftOrNull()!!).isInstanceOf(BookingError.BookingNotFound::class)
    }
}
