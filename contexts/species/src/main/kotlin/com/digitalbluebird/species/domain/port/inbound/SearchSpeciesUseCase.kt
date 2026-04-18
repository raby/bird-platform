package com.digitalbluebird.species.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.species.domain.Species
import com.digitalbluebird.species.domain.SpeciesError

interface SearchSpeciesUseCase {
    fun search(query: String, limit: Int = 20): Either<SpeciesError, List<Species>>
}
