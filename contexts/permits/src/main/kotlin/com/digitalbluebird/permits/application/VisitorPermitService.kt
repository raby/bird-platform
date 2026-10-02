package com.digitalbluebird.permits.application

import com.digitalbluebird.permits.domain.PermitDecision
import com.digitalbluebird.permits.domain.port.inbound.IssueVisitorPermitUseCase
import com.digitalbluebird.permits.domain.port.outbound.ReserveOutcome
import com.digitalbluebird.permits.domain.port.outbound.VisitorPermitRepository
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class VisitorPermitService(
    private val permits: VisitorPermitRepository,
    private val clock: Clock,
) : IssueVisitorPermitUseCase {

    override fun issueFor(bookingId: UUID, hideId: UUID, day: LocalDate, partySize: Int): PermitDecision {
        // Idempotency: a re-delivered BookingConfirmed must not debit the cap twice.
        if (permits.isIssued(bookingId)) return PermitDecision.Issued

        return when (permits.reserve(hideId, day, partySize)) {
            ReserveOutcome.RESERVED -> {
                permits.recordIssuance(bookingId, hideId, day, partySize, clock.instant())
                PermitDecision.Issued
            }
            // No cap configured for this hide/day: nothing to debit, nothing to record.
            ReserveOutcome.NO_CAP -> PermitDecision.Issued
            ReserveOutcome.EXHAUSTED -> PermitDecision.Exhausted
        }
    }
}
