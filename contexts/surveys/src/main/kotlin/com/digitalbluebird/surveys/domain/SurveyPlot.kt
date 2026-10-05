package com.digitalbluebird.surveys.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.surveys.domain.event.PlotClaimed
import com.digitalbluebird.surveys.domain.event.PlotReleased
import com.digitalbluebird.surveys.domain.event.SurveyPlotEvent
import java.time.Instant

/**
 * An event-sourced survey plot. Its state is never stored directly — it is the fold of its event
 * stream ([replay]). Command methods *decide* against the current state and return the event to
 * append rather than mutating; the service persists that event. [version] is the number of events
 * applied, used as the optimistic-concurrency token when appending the next one.
 */
data class SurveyPlot(
    val id: SurveyPlotId,
    val holder: ObserverId?,
    val season: Season?,
    val version: Int,
) {
    val isHeld: Boolean get() = holder != null

    fun claim(observerId: ObserverId, season: Season, now: Instant): Either<SurveyError, PlotClaimed> =
        if (isHeld) {
            SurveyError.PlotAlreadyHeld(id).left()
        } else {
            PlotClaimed(id.value.toString(), observerId.value.toString(), season.year, now).right()
        }

    fun release(observerId: ObserverId, now: Instant): Either<SurveyError, PlotReleased> = when {
        !isHeld -> SurveyError.PlotNotClaimed(id).left()
        holder != observerId -> SurveyError.NotHeldByObserver(id).left()
        else -> PlotReleased(id.value.toString(), observerId.value.toString(), now).right()
    }

    /** Fold one event onto the state. Total over the sealed event type, so adding an event forces a case. */
    fun apply(event: SurveyPlotEvent): SurveyPlot = when (event) {
        is PlotClaimed -> copy(
            holder = ObserverId.fromString(event.observerId),
            season = Season(event.season),
            version = version + 1,
        )
        is PlotReleased -> copy(holder = null, season = null, version = version + 1)
    }

    companion object {
        fun empty(id: SurveyPlotId): SurveyPlot = SurveyPlot(id, holder = null, season = null, version = 0)

        /** Rebuild a plot's current state from its event stream. */
        fun replay(id: SurveyPlotId, events: List<SurveyPlotEvent>): SurveyPlot =
            events.fold(empty(id)) { plot, event -> plot.apply(event) }
    }
}
