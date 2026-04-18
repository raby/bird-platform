package com.digitalbluebird.bookings.application

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.digitalbluebird.bookings.domain.Booking
import com.digitalbluebird.bookings.domain.BookingError
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.IdempotencyKey
import com.digitalbluebird.bookings.domain.PartySize
import com.digitalbluebird.bookings.domain.event.BookingCancelledEvent
import com.digitalbluebird.bookings.domain.event.BookingConfirmedEvent
import com.digitalbluebird.bookings.domain.event.BookingRequestedEvent
import com.digitalbluebird.bookings.domain.port.inbound.CancelBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.ConfirmBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.FindBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingCommand
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingUseCase
import com.digitalbluebird.bookings.domain.port.outbound.BookingRepository
import com.digitalbluebird.bookings.domain.port.outbound.IdempotencyKeysRepository
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.digitalbluebird.shared.infra.outbox.OutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

open class BookingService(
    private val bookings: BookingRepository,
    private val idempotencyKeys: IdempotencyKeysRepository,
    private val outbox: OutboxRepository,
    private val clock: Clock,
    private val objectMapper: ObjectMapper,
) : RequestBookingUseCase, ConfirmBookingUseCase, CancelBookingUseCase, FindBookingUseCase {

    @Transactional
    override fun request(command: RequestBookingCommand): Either<BookingError, Booking> {
        val idempotencyKey = try {
            IdempotencyKey(command.idempotencyKey)
        } catch (e: IllegalArgumentException) {
            return BookingError.InvalidIdempotencyKey(e.message ?: "invalid idempotency key").left()
        }

        idempotencyKeys.findBookingId(idempotencyKey)?.let { existingId ->
            val existing = bookings.findById(existingId)
                ?: return BookingError.BookingNotFound(existingId).left()
            return existing.right()
        }

        val hideId = try {
            HideId(UUID.fromString(command.hideId))
        } catch (e: IllegalArgumentException) {
            return BookingError.InvalidHideId("hideId must be a UUID").left()
        }
        val observerId = try {
            ObserverId(UUID.fromString(command.observerId))
        } catch (e: IllegalArgumentException) {
            return BookingError.InvalidObserverId("observerId must be a UUID").left()
        }
        val slot = try {
            InstantRange(command.slotStart, command.slotEnd)
        } catch (e: IllegalArgumentException) {
            return BookingError.InvalidSlot(e.message ?: "invalid slot").left()
        }
        val partySize = try {
            PartySize(command.partySize)
        } catch (e: IllegalArgumentException) {
            return BookingError.InvalidPartySize(e.message ?: "invalid partySize").left()
        }

        if (bookings.hasOverlappingActiveBooking(hideId, slot)) {
            return BookingError.HideSlotUnavailable(hideId).left()
        }

        val now = clock.instant()
        val booking = Booking.request(
            id = BookingId.random(),
            hideId = hideId,
            observerId = observerId,
            slot = slot,
            partySize = partySize,
            now = now,
        )

        if (!idempotencyKeys.register(idempotencyKey, booking.id)) {
            val raced = idempotencyKeys.findBookingId(idempotencyKey)
                ?: return BookingError.IdempotencyKeyConflict(idempotencyKey).left()
            val existing = bookings.findById(raced)
                ?: return BookingError.BookingNotFound(raced).left()
            return existing.right()
        }

        bookings.insert(booking)

        val event = BookingRequestedEvent(
            bookingId = booking.id.value.toString(),
            hideId = booking.hideId.value.toString(),
            observerId = booking.observerId.value.toString(),
            slotStart = booking.slot.start,
            slotEnd = booking.slot.endExclusive,
            partySize = booking.partySize.value,
            occurredAt = now,
        )
        outbox.save(
            OutboxEntry(
                id = UUID.randomUUID(),
                aggregateType = BookingRequestedEvent.AGGREGATE_TYPE,
                aggregateId = booking.id.value.toString(),
                eventType = BookingRequestedEvent.EVENT_TYPE,
                payload = objectMapper.writeValueAsString(event),
                occurredAt = now,
            ),
        )

        return booking.right()
    }

    @Transactional
    override fun confirm(id: BookingId, expectedVersion: Long): Either<BookingError, Booking> =
        mutate(id, expectedVersion) { current, now ->
            current.confirm(now).map { confirmed ->
                val event = BookingConfirmedEvent(
                    bookingId = confirmed.id.value.toString(),
                    hideId = confirmed.hideId.value.toString(),
                    observerId = confirmed.observerId.value.toString(),
                    occurredAt = now,
                )
                MutationResult(
                    next = confirmed,
                    eventType = BookingConfirmedEvent.EVENT_TYPE,
                    payload = objectMapper.writeValueAsString(event),
                )
            }
        }

    @Transactional
    override fun cancel(id: BookingId, expectedVersion: Long): Either<BookingError, Booking> =
        mutate(id, expectedVersion) { current, now ->
            current.cancel(now).map { cancelled ->
                val event = BookingCancelledEvent(
                    bookingId = cancelled.id.value.toString(),
                    hideId = cancelled.hideId.value.toString(),
                    observerId = cancelled.observerId.value.toString(),
                    occurredAt = now,
                )
                MutationResult(
                    next = cancelled,
                    eventType = BookingCancelledEvent.EVENT_TYPE,
                    payload = objectMapper.writeValueAsString(event),
                )
            }
        }

    @Transactional(readOnly = true)
    override fun findById(id: BookingId): Either<BookingError, Booking> =
        bookings.findById(id)?.right() ?: BookingError.BookingNotFound(id).left()

    private fun mutate(
        id: BookingId,
        expectedVersion: Long,
        step: (Booking, Instant) -> Either<BookingError, MutationResult>,
    ): Either<BookingError, Booking> {
        val current = bookings.findById(id)
            ?: return BookingError.BookingNotFound(id).left()
        if (current.version != expectedVersion) {
            return BookingError.VersionConflict(id, expectedVersion).left()
        }
        val now = clock.instant()
        val result = step(current, now).getOrElse { return it.left() }
        val persisted = bookings.updateIfVersionMatches(result.next, expectedVersion)
            ?: return BookingError.VersionConflict(id, expectedVersion).left()
        outbox.save(
            OutboxEntry(
                id = UUID.randomUUID(),
                aggregateType = BookingRequestedEvent.AGGREGATE_TYPE,
                aggregateId = persisted.id.value.toString(),
                eventType = result.eventType,
                payload = result.payload,
                occurredAt = now,
            ),
        )
        return persisted.right()
    }

    private data class MutationResult(val next: Booking, val eventType: String, val payload: String)
}
