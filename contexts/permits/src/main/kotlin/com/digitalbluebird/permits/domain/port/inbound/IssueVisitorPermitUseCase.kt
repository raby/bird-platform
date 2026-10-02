package com.digitalbluebird.permits.domain.port.inbound

import com.digitalbluebird.permits.domain.PermitDecision
import java.time.LocalDate
import java.util.UUID

/**
 * Issues a visitor permit for a booking against a hide's daily visitor cap. The booking supplies the
 * [partySize] to debit and the [day] (derived from its slot). Idempotent per [bookingId]: re-issuing
 * the same booking returns [PermitDecision.Issued] without debiting the cap twice, so the operation is
 * safe under the at-least-once outbox relay that drives the saga.
 *
 * Hide identity is a raw [UUID] here, not the bookings context's HideId, so the permits context stays
 * independent of bookings.
 */
fun interface IssueVisitorPermitUseCase {
    fun issueFor(bookingId: UUID, hideId: UUID, day: LocalDate, partySize: Int): PermitDecision
}
