package com.digitalbluebird.surveys.domain.event

import java.time.Instant

/**
 * The event-sourcing events for a survey plot: the plot's ownership *is* this append-only stream, so
 * its state is the fold of these events and its full provenance is the log itself. Fields are
 * primitives (not value classes) so they serialize cleanly to the event store's JSONB payload and
 * read back by [SurveyPlotEvent] type, the same idiom the outbox uses.
 */
sealed interface SurveyPlotEvent {
    val occurredAt: Instant
}

data class PlotClaimed(
    val plotId: String,
    val observerId: String,
    val season: Int,
    override val occurredAt: Instant,
) : SurveyPlotEvent {
    companion object {
        const val EVENT_TYPE = "PlotClaimed"
    }
}

data class PlotReleased(
    val plotId: String,
    val observerId: String,
    override val occurredAt: Instant,
) : SurveyPlotEvent {
    companion object {
        const val EVENT_TYPE = "PlotReleased"
    }
}
