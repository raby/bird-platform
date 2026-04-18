package com.digitalbluebird.observations.domain

import com.digitalbluebird.shared.domain.DomainError
import java.time.Instant

sealed interface ObservationError : DomainError {
    data class InvalidObserverId(override val message: String) : ObservationError, DomainError.Validation
    data class InvalidLocation(override val message: String) : ObservationError, DomainError.Validation
    data class InvalidSpeciesId(override val message: String) : ObservationError, DomainError.Validation
    data class InvalidCount(override val message: String) : ObservationError, DomainError.Validation
    data class ObservedAtInFuture(val observedAt: Instant, val now: Instant) : ObservationError, DomainError.Validation {
        override val message: String = "observedAt $observedAt is after now $now"
    }
    data class SightingNotFound(val id: SightingId) : ObservationError, DomainError.NotFound {
        override val message: String = "sighting not found: ${id.value}"
    }
}
