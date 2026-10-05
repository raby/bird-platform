package com.digitalbluebird.surveys.domain

import com.digitalbluebird.shared.domain.DomainError

sealed interface SurveyError : DomainError {
    data class InvalidPlotId(override val message: String) : SurveyError, DomainError.Validation
    data class InvalidObserverId(override val message: String) : SurveyError, DomainError.Validation
    data class InvalidSeason(override val message: String) : SurveyError, DomainError.Validation

    data class PlotAlreadyHeld(val plotId: SurveyPlotId) : SurveyError, DomainError.Conflict {
        override val message: String = "survey plot ${plotId.value} is already claimed for the season"
    }

    data class PlotNotClaimed(val plotId: SurveyPlotId) : SurveyError, DomainError.Conflict {
        override val message: String = "survey plot ${plotId.value} is not currently claimed"
    }

    data class NotHeldByObserver(val plotId: SurveyPlotId) : SurveyError, DomainError.Conflict {
        override val message: String = "survey plot ${plotId.value} is not held by that observer"
    }

    /** The event stream advanced under us between load and append (optimistic concurrency on the stream). */
    data class ConcurrencyConflict(val plotId: SurveyPlotId) : SurveyError, DomainError.Conflict {
        override val message: String = "survey plot ${plotId.value} was modified concurrently; retry"
    }
}
