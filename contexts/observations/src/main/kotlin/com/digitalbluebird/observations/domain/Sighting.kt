package com.digitalbluebird.observations.domain

import com.digitalbluebird.shared.domain.Location
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.shared.domain.SpeciesId
import java.time.Instant

data class Sighting(
    val id: SightingId,
    val observerId: ObserverId,
    val speciesId: SpeciesId,
    val location: Location,
    val observedAt: Instant,
    val count: Count,
    val notes: String?,
    val createdAt: Instant,
) {
    init {
        require(notes == null || notes.length <= 1000) { "notes too long" }
    }
}
