package com.digitalbluebird.expeditions.domain

import java.time.Instant

/**
 * An expedition: a guided outing that books several hides for one party as a single all-or-nothing
 * unit. It is the process manager's state — a persisted state machine ([ExpeditionStatus]) over a set
 * of [legs] — so the orchestration's progress and outcome survive restarts and are inspectable.
 *
 * Pure: the transitions here just compute the next state. Booking and releasing hides (the side effects)
 * and persistence live in the application service that drives this aggregate.
 */
data class Expedition(
    val id: ExpeditionId,
    val leaderId: String,
    val partySize: Int,
    val status: ExpeditionStatus,
    val legs: List<ExpeditionLeg>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val bookedLegs: List<ExpeditionLeg> get() = legs.filter { it.status == LegStatus.BOOKED }

    /** Replace the leg at [index] and bump the timestamp. */
    fun withLeg(index: Int, leg: ExpeditionLeg, at: Instant): Expedition =
        copy(legs = legs.toMutableList().also { it[index] = leg }, updatedAt = at)

    /** Mark the leg holding [bookingId] cancelled. Idempotent: a no-op if no leg has that booking. */
    fun markLegCancelled(bookingId: String, at: Instant): Expedition =
        copy(
            legs = legs.map { if (it.bookingId == bookingId) it.copy(status = LegStatus.CANCELLED) else it },
            updatedAt = at,
        )

    fun to(next: ExpeditionStatus, at: Instant): Expedition = copy(status = next, updatedAt = at)

    companion object {
        /** A fresh expedition about to book its legs, each still PENDING. */
        fun planning(
            id: ExpeditionId,
            leaderId: String,
            partySize: Int,
            legs: List<ExpeditionLeg>,
            at: Instant,
        ): Expedition = Expedition(id, leaderId, partySize, ExpeditionStatus.BOOKING, legs, at, at)
    }
}

enum class ExpeditionStatus { BOOKING, CONFIRMED, COMPENSATING, FAILED }
