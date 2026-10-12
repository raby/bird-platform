package com.digitalbluebird.expeditions.application

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.digitalbluebird.expeditions.domain.Expedition
import com.digitalbluebird.expeditions.domain.ExpeditionError
import com.digitalbluebird.expeditions.domain.ExpeditionId
import com.digitalbluebird.expeditions.domain.ExpeditionLeg
import com.digitalbluebird.expeditions.domain.ExpeditionStatus
import com.digitalbluebird.expeditions.domain.LegStatus
import com.digitalbluebird.expeditions.domain.port.inbound.HideRequest
import com.digitalbluebird.expeditions.domain.port.inbound.PlanExpeditionCommand
import com.digitalbluebird.expeditions.domain.port.outbound.BookingResult
import com.digitalbluebird.expeditions.domain.port.outbound.ExpeditionRepository
import com.digitalbluebird.expeditions.domain.port.outbound.HideBookingGateway
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ExpeditionServiceTest {

    private val now = Instant.parse("2026-05-01T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo = mockk<ExpeditionRepository>(relaxed = true)
    private val gateway = mockk<HideBookingGateway>(relaxed = true)
    private val service = ExpeditionService(repo, gateway, clock)

    private val slotStart = Instant.parse("2026-11-01T08:00:00Z")
    private val slotEnd = Instant.parse("2026-11-01T10:00:00Z")

    private fun command(vararg hides: String) = PlanExpeditionCommand(
        leaderId = "leader-1",
        partySize = 4,
        hides = hides.map { HideRequest(it, slotStart, slotEnd) },
    )

    private fun leg(hide: String, bookingId: String?, status: LegStatus) =
        ExpeditionLeg(hide, slotStart, slotEnd, bookingId, status)

    private fun expedition(status: ExpeditionStatus, vararg legs: ExpeditionLeg) = Expedition(
        id = ExpeditionId.random(), leaderId = "leader-1", partySize = 4,
        status = status, legs = legs.toList(), createdAt = now, updatedAt = now,
    )

    private fun lastSaved(): Expedition {
        val saved = mutableListOf<Expedition>()
        verify { repo.save(capture(saved)) }
        return saved.last()
    }

    @Test
    fun `plan books every hide and confirms the expedition`() {
        every { gateway.book(any(), any(), any(), any(), any()) } returnsMany
            listOf(BookingResult.Booked("bk-A"), BookingResult.Booked("bk-B"))

        val result = service.plan(command("hide-A", "hide-B")).getOrNull()!!

        assertThat(result.status).isEqualTo(ExpeditionStatus.CONFIRMED)
        assertThat(result.legs.map { it.status }).containsExactly(LegStatus.BOOKED, LegStatus.BOOKED)
        assertThat(result.legs.map { it.bookingId }).containsExactly("bk-A", "bk-B")
        verify(exactly = 0) { gateway.cancel(any()) }
    }

    @Test
    fun `a slot clash releases the already-booked hides and fails the expedition`() {
        every { gateway.book("hide-A", any(), any(), any(), any()) } returns BookingResult.Booked("bk-A")
        every { gateway.book("hide-B", any(), any(), any(), any()) } returns BookingResult.Unavailable

        val result = service.plan(command("hide-A", "hide-B", "hide-C")).getOrNull()!!

        assertThat(result.status).isEqualTo(ExpeditionStatus.FAILED)
        assertThat(result.legs.map { it.status })
            .containsExactly(LegStatus.CANCELLED, LegStatus.UNAVAILABLE, LegStatus.PENDING)
        verify(exactly = 1) { gateway.cancel("bk-A") } // the one booked leg is compensated
        verify(exactly = 0) { gateway.book("hide-C", any(), any(), any(), any()) } // stopped at the clash
    }

    @Test
    fun `onBookingCancelled compensates a confirmed expedition and fails it`() {
        val confirmed = expedition(
            ExpeditionStatus.CONFIRMED,
            leg("hide-A", "bk-A", LegStatus.BOOKED),
            leg("hide-B", "bk-B", LegStatus.BOOKED),
        )
        every { repo.findByLegBookingId("bk-A") } returns confirmed

        service.onBookingCancelled("bk-A") // the permits saga cancelled hide-A's booking

        verify(exactly = 1) { gateway.cancel("bk-B") } // the other leg is released
        verify(exactly = 0) { gateway.cancel("bk-A") } // the already-cancelled one is not re-cancelled
        assertThat(lastSaved().status).isEqualTo(ExpeditionStatus.FAILED)
    }

    @Test
    fun `onBookingCancelled does not compensate again for an already-failing expedition`() {
        val failing = expedition(ExpeditionStatus.FAILED, leg("hide-A", "bk-A", LegStatus.CANCELLED))
        every { repo.findByLegBookingId("bk-A") } returns failing

        service.onBookingCancelled("bk-A")

        verify(exactly = 0) { gateway.cancel(any()) } // no compensation loop
    }

    @Test
    fun `onBookingCancelled ignores a booking that belongs to no expedition`() {
        every { repo.findByLegBookingId("unknown") } returns null

        service.onBookingCancelled("unknown")

        verify(exactly = 0) { gateway.cancel(any()) }
        verify(exactly = 0) { repo.save(any()) }
    }

    @Test
    fun `plan rejects a request with no hides`() {
        val result = service.plan(PlanExpeditionCommand("leader-1", 4, emptyList()))
        assertThat(result.leftOrNull()!!).isInstanceOf(ExpeditionError.NoHides::class)
    }
}
