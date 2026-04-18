package com.digitalbluebird.observations.domain.port.outbound

import com.digitalbluebird.observations.domain.Sighting
import com.digitalbluebird.observations.domain.SightingId

interface SightingRepository {
    fun save(sighting: Sighting): Sighting
    fun findById(id: SightingId): Sighting?
}
