package com.digitalbluebird.bookings.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class BookingStateMachineTest {

    private val now: Instant = Instant.parse("2026-04-18T09:00:00Z")

    private fun requested(): Booking = Booking.request(
        id = BookingId.random(),
        hideId = HideId(UUID.randomUUID()),
        observerId = ObserverId(UUID.randomUUID()),
        slot = InstantRange(now.plusSeconds(3600), now.plusSeconds(7200)),
        partySize = PartySize(2),
        now = now,
    )

    @Test
    fun `requested can be confirmed`() {
        val booking = requested()
        val result = booking.confirm(now.plusSeconds(10))
        val confirmed = result.getOrNull()!!
        assertThat(confirmed.status).isEqualTo(BookingStatus.CONFIRMED)
        assertThat(confirmed.updatedAt).isEqualTo(now.plusSeconds(10))
    }

    @Test
    fun `requested can be cancelled directly`() {
        val booking = requested()
        val result = booking.cancel(now.plusSeconds(10))
        assertThat(result.getOrNull()!!.status).isEqualTo(BookingStatus.CANCELLED)
    }

    @Test
    fun `confirmed can be cancelled`() {
        val confirmed = requested().confirm(now).getOrNull()!!
        val cancelled = confirmed.cancel(now.plusSeconds(30)).getOrNull()!!
        assertThat(cancelled.status).isEqualTo(BookingStatus.CANCELLED)
    }

    @Test
    fun `cancelled cannot be confirmed`() {
        val cancelled = requested().cancel(now).getOrNull()!!
        val result = cancelled.confirm(now.plusSeconds(1))
        val err = result.leftOrNull()!!
        assertThat(err).isInstanceOf(BookingError.StateTransitionNotAllowed::class)
        err as BookingError.StateTransitionNotAllowed
        assertThat(err.current).isEqualTo(BookingStatus.CANCELLED)
        assertThat(err.requested).isEqualTo(BookingStatus.CONFIRMED)
    }

    @Test
    fun `cancelled cannot be cancelled again`() {
        val cancelled = requested().cancel(now).getOrNull()!!
        val result = cancelled.cancel(now.plusSeconds(1))
        assertThat(result.leftOrNull()!!).isInstanceOf(BookingError.StateTransitionNotAllowed::class)
    }

    @Test
    fun `confirmed cannot be re-confirmed`() {
        val confirmed = requested().confirm(now).getOrNull()!!
        val result = confirmed.confirm(now.plusSeconds(1))
        assertThat(result.leftOrNull()!!).isInstanceOf(BookingError.StateTransitionNotAllowed::class)
    }

    @Test
    fun `isTerminal is true only for CANCELLED`() {
        assertThat(BookingStatus.REQUESTED.isTerminal).isFalse()
        assertThat(BookingStatus.CONFIRMED.isTerminal).isFalse()
        assertThat(BookingStatus.CANCELLED.isTerminal).isTrue()
    }
}
