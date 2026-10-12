package com.digitalbluebird.expeditions.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.expeditions.domain.Expedition
import com.digitalbluebird.expeditions.domain.ExpeditionError
import java.time.Instant

/**
 * Plan an expedition: book every requested hide as one all-or-nothing unit. Returns the resulting
 * [Expedition] on the Right — CONFIRMED when every hide was booked, FAILED when a slot clash forced the
 * already-booked hides to be released. A Left is only for an invalid request, not a failed outing.
 */
fun interface PlanExpeditionUseCase {
    fun plan(command: PlanExpeditionCommand): Either<ExpeditionError, Expedition>
}

data class PlanExpeditionCommand(
    val leaderId: String,
    val partySize: Int,
    val hides: List<HideRequest>,
)

data class HideRequest(
    val hideId: String,
    val slotStart: Instant,
    val slotEnd: Instant,
)
