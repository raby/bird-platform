package com.digitalbluebird.species.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.species.domain.SpeciesError

interface ImportTaxonomyUseCase {
    fun importAll(): Either<SpeciesError, ImportResult>
}

data class ImportResult(val imported: Int)
