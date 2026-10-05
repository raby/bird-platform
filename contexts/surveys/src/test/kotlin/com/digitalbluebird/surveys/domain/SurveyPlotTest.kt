package com.digitalbluebird.surveys.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.surveys.domain.event.PlotClaimed
import com.digitalbluebird.surveys.domain.event.PlotReleased
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SurveyPlotTest {

    private val plotId = SurveyPlotId(UUID.fromString("a0000000-0000-0000-0000-000000000001"))
    private val alice = ObserverId.random()
    private val bob = ObserverId.random()
    private val now: Instant = Instant.parse("2026-05-01T09:00:00Z")

    private fun claimed(observer: ObserverId, year: Int) =
        PlotClaimed(plotId.value.toString(), observer.value.toString(), year, now)

    private fun released(observer: ObserverId) =
        PlotReleased(plotId.value.toString(), observer.value.toString(), now)

    @Test
    fun `an empty plot is not held`() {
        assertThat(SurveyPlot.empty(plotId).isHeld).isFalse()
    }

    @Test
    fun `claiming an unclaimed plot produces PlotClaimed`() {
        val event = SurveyPlot.empty(plotId).claim(alice, Season(2026), now).getOrNull()!!
        assertThat(event.observerId).isEqualTo(alice.value.toString())
        assertThat(event.season).isEqualTo(2026)
    }

    @Test
    fun `claiming a held plot is rejected`() {
        val held = SurveyPlot.empty(plotId).apply(claimed(alice, 2026))
        assertThat(held.claim(bob, Season(2026), now).leftOrNull()!!).isInstanceOf(SurveyError.PlotAlreadyHeld::class)
    }

    @Test
    fun `releasing is allowed only for the current holder`() {
        val held = SurveyPlot.empty(plotId).apply(claimed(alice, 2026))
        assertThat(held.release(alice, now).getOrNull()).isNotNull()
        assertThat(held.release(bob, now).leftOrNull()!!).isInstanceOf(SurveyError.NotHeldByObserver::class)
    }

    @Test
    fun `releasing an unclaimed plot is rejected`() {
        assertThat(SurveyPlot.empty(plotId).release(alice, now).leftOrNull()!!).isInstanceOf(SurveyError.PlotNotClaimed::class)
    }

    @Test
    fun `replaying claim then release leaves the plot unheld at version 2`() {
        val plot = SurveyPlot.replay(plotId, listOf(claimed(alice, 2026), released(alice)))
        assertThat(plot.isHeld).isFalse()
        assertThat(plot.version).isEqualTo(2)
    }

    @Test
    fun `replaying claim, release, re-claim shows the latest holder and season`() {
        val plot = SurveyPlot.replay(plotId, listOf(claimed(alice, 2026), released(alice), claimed(bob, 2027)))
        assertThat(plot.holder).isEqualTo(bob)
        assertThat(plot.season).isEqualTo(Season(2027))
        assertThat(plot.version).isEqualTo(3)
    }
}
