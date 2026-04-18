package com.digitalbluebird.species.domain.port.outbound

import com.digitalbluebird.shared.domain.SpeciesId
import com.digitalbluebird.species.domain.Species

interface SpeciesReadModel {
    fun indexAll(species: List<Species>)
    fun search(query: String, limit: Int): List<Species>
    fun findById(id: SpeciesId): Species?
}
