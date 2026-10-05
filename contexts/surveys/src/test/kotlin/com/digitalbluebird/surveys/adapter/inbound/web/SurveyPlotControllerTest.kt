package com.digitalbluebird.surveys.adapter.inbound.web

import arrow.core.left
import arrow.core.right
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.surveys.domain.SurveyError
import com.digitalbluebird.surveys.domain.SurveyPlot
import com.digitalbluebird.surveys.domain.SurveyPlotId
import com.digitalbluebird.surveys.domain.event.PlotClaimed
import com.digitalbluebird.surveys.domain.port.inbound.ClaimPlotUseCase
import com.digitalbluebird.surveys.domain.port.inbound.ReleasePlotUseCase
import com.digitalbluebird.surveys.domain.port.inbound.ViewPlotUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SurveyPlotControllerTest {

    private val claimPlot: ClaimPlotUseCase = mockk()
    private val releasePlot: ReleasePlotUseCase = mockk()
    private val viewPlot: ViewPlotUseCase = mockk()
    private val controller = SurveyPlotController(claimPlot, releasePlot, viewPlot)

    private val plotId = SurveyPlotId(UUID.fromString("a0000000-0000-0000-0000-000000000001"))
    private val pid = plotId.value.toString()
    private val alice = ObserverId.random()
    private val now: Instant = Instant.parse("2026-05-01T09:00:00Z")

    @Test
    fun `claim returns 200 with the held plot`() {
        val held = SurveyPlot.empty(plotId).apply(PlotClaimed(pid, alice.value.toString(), 2026, now))
        every { claimPlot.claim(pid, alice.value.toString(), 2026) } returns held.right()

        val response = controller.claim(pid, ClaimPlotRequest(alice.value.toString(), 2026))

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat((response.body as PlotResponse).holder).isEqualTo(alice.value.toString())
    }

    @Test
    fun `claim on a held plot maps to 409`() {
        every { claimPlot.claim(any(), any(), any()) } returns SurveyError.PlotAlreadyHeld(plotId).left()

        val response = controller.claim(pid, ClaimPlotRequest(alice.value.toString(), 2026))

        assertThat(response.statusCode.value()).isEqualTo(409)
        assertThat((response.body as ErrorResponse).code).isEqualTo("PlotAlreadyHeld")
    }

    @Test
    fun `an invalid plot id maps to 400`() {
        every { viewPlot.current("bad") } returns SurveyError.InvalidPlotId("plotId must be a UUID").left()

        val response = controller.current("bad")

        assertThat(response.statusCode.value()).isEqualTo(400)
    }

    @Test
    fun `history returns 200 with the ownership event list`() {
        every { viewPlot.history(pid) } returns listOf(PlotClaimed(pid, alice.value.toString(), 2026, now)).right()

        val response = controller.history(pid)

        assertThat(response.statusCode.value()).isEqualTo(200)
        @Suppress("UNCHECKED_CAST")
        val body = response.body as List<PlotEventResponse>
        assertThat(body[0].type).isEqualTo("PlotClaimed")
    }
}
