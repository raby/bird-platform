package com.digitalbluebird.surveys.application

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.surveys.domain.Season
import com.digitalbluebird.surveys.domain.SurveyError
import com.digitalbluebird.surveys.domain.SurveyPlot
import com.digitalbluebird.surveys.domain.SurveyPlotId
import com.digitalbluebird.surveys.domain.event.SurveyPlotEvent
import com.digitalbluebird.surveys.domain.port.inbound.ClaimPlotUseCase
import com.digitalbluebird.surveys.domain.port.inbound.ReleasePlotUseCase
import com.digitalbluebird.surveys.domain.port.inbound.ViewPlotUseCase
import com.digitalbluebird.surveys.domain.port.outbound.SurveyPlotEventStore
import java.time.Clock

/**
 * Event-sourced survey-plot commands and queries. A command replays the plot from its stream, lets the
 * aggregate decide (producing an event), and appends it at the replayed version. That append is a
 * single atomic INSERT whose (plot_id, sequence) primary key is the optimistic-concurrency guard, so
 * no surrounding transaction is needed: a concurrent writer that took the slot makes the append fail,
 * surfaced here as [SurveyError.ConcurrencyConflict].
 */
class SurveyPlotService(
    private val events: SurveyPlotEventStore,
    private val clock: Clock,
) : ClaimPlotUseCase, ReleasePlotUseCase, ViewPlotUseCase {

    override fun claim(plotId: String, observerId: String, season: Int): Either<SurveyError, SurveyPlot> {
        val id = parsePlotId(plotId) ?: return SurveyError.InvalidPlotId("plotId must be a UUID").left()
        val observer = parseObserver(observerId) ?: return SurveyError.InvalidObserverId("observerId must be a UUID").left()
        val seasonValue = parseSeason(season) ?: return SurveyError.InvalidSeason("season year out of range: $season").left()

        val plot = SurveyPlot.replay(id, events.load(id))
        return plot.claim(observer, seasonValue, clock.instant()).flatMap { event -> appendAndFold(plot, event) }
    }

    override fun release(plotId: String, observerId: String): Either<SurveyError, SurveyPlot> {
        val id = parsePlotId(plotId) ?: return SurveyError.InvalidPlotId("plotId must be a UUID").left()
        val observer = parseObserver(observerId) ?: return SurveyError.InvalidObserverId("observerId must be a UUID").left()

        val plot = SurveyPlot.replay(id, events.load(id))
        return plot.release(observer, clock.instant()).flatMap { event -> appendAndFold(plot, event) }
    }

    override fun current(plotId: String): Either<SurveyError, SurveyPlot> {
        val id = parsePlotId(plotId) ?: return SurveyError.InvalidPlotId("plotId must be a UUID").left()
        return SurveyPlot.replay(id, events.load(id)).right()
    }

    override fun history(plotId: String): Either<SurveyError, List<SurveyPlotEvent>> {
        val id = parsePlotId(plotId) ?: return SurveyError.InvalidPlotId("plotId must be a UUID").left()
        return events.load(id).right()
    }

    private fun appendAndFold(plot: SurveyPlot, event: SurveyPlotEvent): Either<SurveyError, SurveyPlot> =
        if (events.append(plot.id, plot.version, event)) {
            plot.apply(event).right()
        } else {
            SurveyError.ConcurrencyConflict(plot.id).left()
        }

    private fun parsePlotId(s: String): SurveyPlotId? = runCatching { SurveyPlotId.fromString(s) }.getOrNull()
    private fun parseObserver(s: String): ObserverId? = runCatching { ObserverId.fromString(s) }.getOrNull()
    private fun parseSeason(year: Int): Season? = runCatching { Season(year) }.getOrNull()
}
