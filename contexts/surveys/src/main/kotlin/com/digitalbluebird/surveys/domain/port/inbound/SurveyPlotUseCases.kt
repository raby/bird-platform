package com.digitalbluebird.surveys.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.surveys.domain.SurveyError
import com.digitalbluebird.surveys.domain.SurveyPlot
import com.digitalbluebird.surveys.domain.event.SurveyPlotEvent

/** Claim an unclaimed survey plot for an observer and season. */
fun interface ClaimPlotUseCase {
    fun claim(plotId: String, observerId: String, season: Int): Either<SurveyError, SurveyPlot>
}

/** Release a plot the observer currently holds. */
fun interface ReleasePlotUseCase {
    fun release(plotId: String, observerId: String): Either<SurveyError, SurveyPlot>
}

/** Read a plot's current state (the fold of its stream) and its full ownership history (the stream). */
interface ViewPlotUseCase {
    fun current(plotId: String): Either<SurveyError, SurveyPlot>
    fun history(plotId: String): Either<SurveyError, List<SurveyPlotEvent>>
}
