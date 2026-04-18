package com.digitalbluebird.observations.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.observations.domain.ObservationError
import com.digitalbluebird.observations.domain.Sighting
import com.digitalbluebird.observations.domain.SightingId

interface FindSightingUseCase {
    fun findById(id: SightingId): Either<ObservationError, Sighting>
}
