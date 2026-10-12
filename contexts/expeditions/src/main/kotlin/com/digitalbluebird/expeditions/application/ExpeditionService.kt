package com.digitalbluebird.expeditions.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.digitalbluebird.expeditions.domain.Expedition
import com.digitalbluebird.expeditions.domain.ExpeditionError
import com.digitalbluebird.expeditions.domain.ExpeditionId
import com.digitalbluebird.expeditions.domain.ExpeditionLeg
import com.digitalbluebird.expeditions.domain.ExpeditionStatus
import com.digitalbluebird.expeditions.domain.LegStatus
import com.digitalbluebird.expeditions.domain.port.inbound.HandleBookingCancelledUseCase
import com.digitalbluebird.expeditions.domain.port.inbound.PlanExpeditionCommand
import com.digitalbluebird.expeditions.domain.port.inbound.PlanExpeditionUseCase
import com.digitalbluebird.expeditions.domain.port.inbound.ViewExpeditionUseCase
import com.digitalbluebird.expeditions.domain.port.outbound.BookingResult
import com.digitalbluebird.expeditions.domain.port.outbound.ExpeditionRepository
import com.digitalbluebird.expeditions.domain.port.outbound.HideBookingGateway
import java.time.Clock

/**
 * The expedition process manager. [plan] orchestrates booking every hide as one all-or-nothing unit,
 * compensating (releasing the hides booked so far) the moment a slot clash makes the outing impossible.
 * [onBookingCancelled] is the async half: if a hide the expedition already holds is cancelled out from
 * under it later — the visitor-permit saga tripping a daily cap — the expedition releases its other
 * hides and fails.
 *
 * Each step is its own local transaction (a booking is requested + confirmed, then committed) and the
 * state is persisted as it advances, so this is a saga / process manager: compensation, not a
 * distributed transaction. It is deliberately NOT @Transactional — wrapping the steps in one
 * transaction would roll partial work back automatically and make the compensation path dead code.
 *
 * Persisting after each step is also the basis for recovery: a plan interrupted mid-flight leaves a
 * stuck BOOKING expedition (and possibly a booking it had made but not yet recorded). Resuming or
 * compensating those would be a scheduled sweep over stale expeditions — out of scope here, but the
 * durable state is what it would run on.
 */
class ExpeditionService(
    private val expeditions: ExpeditionRepository,
    private val hides: HideBookingGateway,
    private val clock: Clock,
) : PlanExpeditionUseCase, ViewExpeditionUseCase, HandleBookingCancelledUseCase {

    override fun plan(command: PlanExpeditionCommand): Either<ExpeditionError, Expedition> {
        validate(command)?.let { return it.left() }

        var expedition = Expedition.planning(
            id = ExpeditionId.random(),
            leaderId = command.leaderId,
            partySize = command.partySize,
            legs = command.hides.map {
                ExpeditionLeg(it.hideId, it.slotStart, it.slotEnd, bookingId = null, status = LegStatus.PENDING)
            },
            at = clock.instant(),
        )
        expeditions.save(expedition) // durable record before any booking, so a crash leaves a trace

        // Book each hide in turn, persisting progress; stop at the first that cannot be held.
        var clashed = false
        for ((i, leg) in expedition.legs.withIndex()) {
            when (val result = hides.book(leg.hideId, command.leaderId, leg.slotStart, leg.slotEnd, command.partySize)) {
                is BookingResult.Booked -> {
                    expedition = expedition.withLeg(
                        i,
                        leg.copy(bookingId = result.bookingId, status = LegStatus.BOOKED),
                        clock.instant(),
                    )
                    expeditions.save(expedition)
                }

                BookingResult.Unavailable -> {
                    expedition = expedition.withLeg(i, leg.copy(status = LegStatus.UNAVAILABLE), clock.instant())
                    clashed = true
                    break
                }
            }
        }

        expedition = if (clashed) compensate(expedition) else expedition.to(ExpeditionStatus.CONFIRMED, clock.instant())
        expeditions.save(expedition)
        return expedition.right()
    }

    override fun findById(id: String): Either<ExpeditionError, Expedition> {
        val expeditionId = runCatching { ExpeditionId.fromString(id) }.getOrNull()
            ?: return ExpeditionError.InvalidExpeditionId("not a valid expedition id: $id").left()
        return expeditions.findById(expeditionId)?.right() ?: ExpeditionError.ExpeditionNotFound(expeditionId).left()
    }

    override fun onBookingCancelled(bookingId: String) {
        val expedition = expeditions.findByLegBookingId(bookingId) ?: return // not an expedition booking
        when (expedition.status) {
            ExpeditionStatus.BOOKING, ExpeditionStatus.CONFIRMED -> {
                // A hide we were holding was cancelled under us → release the rest and fail the outing.
                // This runs inside the outbox relay's transaction (a BookingCancelled handler drives it),
                // so the cancels and the single save commit atomically with the event being published.
                expeditions.save(compensate(expedition.markLegCancelled(bookingId, clock.instant())))
            }

            ExpeditionStatus.COMPENSATING, ExpeditionStatus.FAILED -> {
                // Already failing — a duplicate, or a cancel this compensation itself triggered. Just
                // record the leg; do not compensate again (that would loop).
                expeditions.save(expedition.markLegCancelled(bookingId, clock.instant()))
            }
        }
    }

    /** Release every still-booked leg, then mark the expedition FAILED. */
    private fun compensate(expedition: Expedition): Expedition {
        var e = expedition.to(ExpeditionStatus.COMPENSATING, clock.instant())
        for (leg in e.bookedLegs) {
            hides.cancel(leg.bookingId!!)
            e = e.markLegCancelled(leg.bookingId, clock.instant())
        }
        return e.to(ExpeditionStatus.FAILED, clock.instant())
    }

    private fun validate(command: PlanExpeditionCommand): ExpeditionError? = when {
        command.leaderId.isBlank() -> ExpeditionError.InvalidLeader("leaderId must not be blank")
        command.partySize < 1 -> ExpeditionError.InvalidPartySize("partySize must be at least 1")
        command.hides.isEmpty() -> ExpeditionError.NoHides("an expedition needs at least one hide")
        command.hides.any { !it.slotStart.isBefore(it.slotEnd) } ->
            ExpeditionError.InvalidSlot("each slot must start before it ends")
        else -> null
    }
}
