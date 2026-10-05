package com.digitalbluebird.surveys.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.surveys.domain.SurveyError
import com.digitalbluebird.surveys.domain.SurveyPlotId
import com.digitalbluebird.surveys.domain.event.PlotClaimed
import com.digitalbluebird.surveys.domain.event.SurveyPlotEvent
import com.digitalbluebird.surveys.domain.port.outbound.SurveyPlotEventStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SurveyPlotServiceTest {

    private val now: Instant = Instant.parse("2026-05-01T09:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val store: SurveyPlotEventStore = mockk(relaxed = true)
    private val service = SurveyPlotService(store, clock)

    private val plotId = SurveyPlotId(UUID.fromString("a0000000-0000-0000-0000-000000000001"))
    private val pid = plotId.value.toString()
    private val alice = ObserverId.random().value.toString()

    @Test
    fun `claim on an empty stream appends at version 0 and returns the held plot`() {
        every { store.load(plotId) } returns emptyList()
        every { store.append(plotId, 0, any()) } returns true

        val plot = service.claim(pid, alice, 2026).getOrNull()!!

        assertThat(plot.isHeld).isTrue()
        assertThat(plot.version).isEqualTo(1)
        verify(exactly = 1) { store.append(plotId, 0, any()) }
    }

    @Test
    fun `claim on a held plot does not append`() {
        every { store.load(plotId) } returns listOf(PlotClaimed(pid, alice, 2026, now))

        assertThat(service.claim(pid, ObserverId.random().value.toString(), 2026).leftOrNull()!!)
            .isInstanceOf(SurveyError.PlotAlreadyHeld::class)
        verify(exactly = 0) { store.append(any(), any(), any()) }
    }

    @Test
    fun `a lost append race surfaces as ConcurrencyConflict`() {
        every { store.load(plotId) } returns emptyList()
        every { store.append(plotId, 0, any()) } returns false

        assertThat(service.claim(pid, alice, 2026).leftOrNull()!!).isInstanceOf(SurveyError.ConcurrencyConflict::class)
    }

    @Test
    fun `an invalid plot id is rejected before touching the store`() {
        assertThat(service.claim("not-a-uuid", alice, 2026).leftOrNull()!!).isInstanceOf(SurveyError.InvalidPlotId::class)
        verify(exactly = 0) { store.load(any()) }
    }

    @Test
    fun `an out-of-range season is rejected`() {
        assertThat(service.claim(pid, alice, 1500).leftOrNull()!!).isInstanceOf(SurveyError.InvalidSeason::class)
    }

    @Test
    fun `history returns the raw stream`() {
        val events: List<SurveyPlotEvent> = listOf(PlotClaimed(pid, alice, 2026, now))
        every { store.load(plotId) } returns events

        assertThat(service.history(pid).getOrNull()).isEqualTo(events)
    }
}
