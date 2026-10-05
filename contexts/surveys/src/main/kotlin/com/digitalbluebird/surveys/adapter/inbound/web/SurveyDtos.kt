package com.digitalbluebird.surveys.adapter.inbound.web

import com.digitalbluebird.surveys.domain.SurveyPlot
import com.digitalbluebird.surveys.domain.event.PlotClaimed
import com.digitalbluebird.surveys.domain.event.PlotReleased
import com.digitalbluebird.surveys.domain.event.SurveyPlotEvent
import java.time.Instant

data class ClaimPlotRequest(val observerId: String, val season: Int)

data class ReleasePlotRequest(val observerId: String)

/** A plot's current state — the fold of its stream, so `version` is the number of events behind it. */
data class PlotResponse(
    val plotId: String,
    val held: Boolean,
    val holder: String?,
    val season: Int?,
    val version: Int,
) {
    companion object {
        fun from(p: SurveyPlot) = PlotResponse(
            plotId = p.id.value.toString(),
            held = p.isHeld,
            holder = p.holder?.value?.toString(),
            season = p.season?.year,
            version = p.version,
        )
    }
}

/** One entry in a plot's ownership history — an event from its stream, in order. */
data class PlotEventResponse(
    val type: String,
    val observerId: String,
    val season: Int?,
    val occurredAt: Instant,
) {
    companion object {
        fun from(e: SurveyPlotEvent): PlotEventResponse = when (e) {
            is PlotClaimed -> PlotEventResponse(PlotClaimed.EVENT_TYPE, e.observerId, e.season, e.occurredAt)
            is PlotReleased -> PlotEventResponse(PlotReleased.EVENT_TYPE, e.observerId, null, e.occurredAt)
        }
    }
}

data class ErrorResponse(val code: String, val message: String)
