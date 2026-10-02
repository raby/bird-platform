package com.digitalbluebird.permits.domain.port.outbound

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The outcome of an atomic [VisitorPermitRepository.reserve] against the day's cap. */
enum class ReserveOutcome {
    /** The party size fit under the configured cap and the day's issued count was incremented. */
    RESERVED,

    /** No cap is configured for that hide and day, so there is nothing to debit (unlimited). */
    NO_CAP,

    /** A cap is configured but the party size would exceed it. */
    EXHAUSTED,
}

interface VisitorPermitRepository {
    /** True when a permit was already issued for this booking — the idempotency guard. */
    fun isIssued(bookingId: UUID): Boolean

    /**
     * Atomically add [partySize] to the day's issued count iff it stays within the configured cap.
     * The increment is a single conditional UPDATE, so concurrent reserves cannot exceed the cap.
     */
    fun reserve(hideId: UUID, day: LocalDate, partySize: Int): ReserveOutcome

    /** Record that [bookingId] holds a permit (the idempotency ledger), for a [ReserveOutcome.RESERVED]. */
    fun recordIssuance(bookingId: UUID, hideId: UUID, day: LocalDate, partySize: Int, at: Instant)
}
