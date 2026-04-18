package com.digitalbluebird.species.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.digitalbluebird.species.domain.SpeciesError
import com.digitalbluebird.species.domain.port.inbound.ImportResult
import com.digitalbluebird.species.domain.port.inbound.ImportTaxonomyUseCase
import com.digitalbluebird.species.domain.port.outbound.SpeciesReadModel
import com.digitalbluebird.species.domain.port.outbound.TaxonomyGateway

class SpeciesImportService(
    private val gateway: TaxonomyGateway,
    private val readModel: SpeciesReadModel,
) : ImportTaxonomyUseCase {

    override fun importAll(): Either<SpeciesError, ImportResult> =
        try {
            val species = gateway.fetchSpecies()
            readModel.indexAll(species)
            ImportResult(imported = species.size).right()
        } catch (e: RuntimeException) {
            SpeciesError.TaxonomyImportFailed(e.message ?: "import failed", e).left()
        }
}
