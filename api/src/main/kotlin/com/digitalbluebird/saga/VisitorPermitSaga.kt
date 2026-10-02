package com.digitalbluebird.saga

import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.BookingStatus
import com.digitalbluebird.bookings.domain.event.BookingConfirmedEvent
import com.digitalbluebird.bookings.domain.port.inbound.CancelBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.FindBookingUseCase
import com.digitalbluebird.permits.domain.PermitDecision
import com.digitalbluebird.permits.domain.port.inbound.IssueVisitorPermitUseCase
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.digitalbluebird.shared.infra.outbox.OutboxHandler
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.ZoneOffset
import java.util.UUID

/**
 * Cross-context saga: when a booking is confirmed, debit a visitor permit against the hide's daily
 * cap; if the cap is exhausted, compensate by cancelling the over-cap booking.
 *
 * It runs as an [OutboxHandler] inside the relay's single transaction, so the permit debit, the
 * compensating cancel, and the resulting BookingCancelled event all commit atomically with the
 * BookingConfirmed entry being marked published. Both steps are idempotent — the permit ledger is
 * keyed by bookingId, and cancelling an already-cancelled booking is a state-machine no-op — so the
 * at-least-once relay may re-deliver safely.
 *
 * It lives in the api module (the composition root): the one place that may depend on both the
 * permits and bookings inbound ports without coupling the two contexts to each other.
 */
class VisitorPermitSaga(
    private val issueVisitorPermit: IssueVisitorPermitUseCase,
    private val findBooking: FindBookingUseCase,
    private val cancelBooking: CancelBookingUseCase,
    private val objectMapper: ObjectMapper,
) : OutboxHandler {

    override fun handle(entry: OutboxEntry) {
        if (entry.eventType != BookingConfirmedEvent.EVENT_TYPE) return
        val event = objectMapper.readValue(entry.payload, BookingConfirmedEvent::class.java)

        val bookingId = UUID.fromString(event.bookingId)
        // The cap is a daily one; take the slot's calendar day in UTC.
        val day = event.slotStart.atZone(ZoneOffset.UTC).toLocalDate()

        when (issueVisitorPermit.issueFor(bookingId, UUID.fromString(event.hideId), day, event.partySize)) {
            PermitDecision.Issued -> Unit
            PermitDecision.Exhausted -> compensate(BookingId(bookingId))
        }
    }

    /**
     * Compensation: cancel the booking that tipped the hide over its daily cap. Read it first for the
     * current version — the optimistic-lock token that BookingConfirmedEvent does not carry — and skip
     * if it is no longer CONFIRMED (already cancelled, e.g. on a re-delivery), so this stays idempotent.
     */
    private fun compensate(bookingId: BookingId) {
        val booking = findBooking.findById(bookingId).getOrNull() ?: return
        if (booking.status == BookingStatus.CONFIRMED) {
            cancelBooking.cancel(booking.id, booking.version)
        }
    }
}
