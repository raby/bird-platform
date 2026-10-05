package com.digitalbluebird.surveys.adapter.inbound.web

import com.digitalbluebird.shared.domain.DomainError
import com.digitalbluebird.surveys.domain.SurveyError
import com.digitalbluebird.surveys.domain.port.inbound.ClaimPlotUseCase
import com.digitalbluebird.surveys.domain.port.inbound.ReleasePlotUseCase
import com.digitalbluebird.surveys.domain.port.inbound.ViewPlotUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/plots")
class SurveyPlotController(
    private val claimPlot: ClaimPlotUseCase,
    private val releasePlot: ReleasePlotUseCase,
    private val viewPlot: ViewPlotUseCase,
) {

    @PostMapping("/{id}/claim")
    fun claim(@PathVariable id: String, @RequestBody body: ClaimPlotRequest): ResponseEntity<Any> =
        claimPlot.claim(id, body.observerId, body.season).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.ok(PlotResponse.from(it)) },
        )

    @PostMapping("/{id}/release")
    fun release(@PathVariable id: String, @RequestBody body: ReleasePlotRequest): ResponseEntity<Any> =
        releasePlot.release(id, body.observerId).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.ok(PlotResponse.from(it)) },
        )

    @GetMapping("/{id}")
    fun current(@PathVariable id: String): ResponseEntity<Any> =
        viewPlot.current(id).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.ok(PlotResponse.from(it)) },
        )

    // The provenance showcase: a plot's full ownership history is just its event stream, in order.
    @GetMapping("/{id}/history")
    fun history(@PathVariable id: String): ResponseEntity<Any> =
        viewPlot.history(id).fold(
            ifLeft = { it.toResponse() },
            ifRight = { events -> ResponseEntity.ok(events.map(PlotEventResponse::from)) },
        )

    private fun SurveyError.toResponse(): ResponseEntity<Any> {
        val status = when (this) {
            is DomainError.Validation -> 400
            is DomainError.Conflict -> 409
            is DomainError.NotFound -> 404
            else -> 500
        }
        return ResponseEntity.status(status).body(ErrorResponse(this::class.simpleName ?: "ERROR", message))
    }
}
