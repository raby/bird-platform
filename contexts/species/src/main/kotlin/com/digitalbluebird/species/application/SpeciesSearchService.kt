package com.digitalbluebird.species.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.digitalbluebird.species.domain.Species
import com.digitalbluebird.species.domain.SpeciesError
import com.digitalbluebird.species.domain.port.inbound.SearchSpeciesUseCase
import com.digitalbluebird.species.domain.port.outbound.SpeciesReadModel

class SpeciesSearchService(
    private val readModel: SpeciesReadModel,
) : SearchSpeciesUseCase {

    override fun search(query: String, limit: Int): Either<SpeciesError, List<Species>> {
        val q = query.trim()
        if (q.length < 2) return SpeciesError.InvalidQuery("query must be at least 2 characters").left()
        val capped = limit.coerceIn(1, 100)
        return readModel.search(q, capped).right()
    }
}
