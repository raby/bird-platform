package com.digitalbluebird.observations.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.observations.domain.ObservationError
import com.digitalbluebird.observations.domain.Sighting
import java.time.Instant

interface RecordSightingUseCase {
    fun record(command: RecordSightingCommand): Either<ObservationError, Sighting>
}

data class RecordSightingCommand(
    val observerId: String,
    val speciesId: String,
    val latitude: Double,
    val longitude: Double,
    val observedAt: Instant,
    val count: Int,
    val notes: String?,
)
