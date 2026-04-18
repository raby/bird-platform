package com.digitalbluebird.species.domain

import com.digitalbluebird.shared.domain.SpeciesId

data class Species(
    val id: SpeciesId,
    val scientificName: String,
    val commonNames: List<CommonName>,
    val taxonomy: TaxonomicClassification,
    val conservationStatus: ConservationStatus,
) {
    init {
        require(scientificName.isNotBlank()) { "scientificName must not be blank" }
        require(commonNames.isNotEmpty()) { "at least one common name required" }
        val langs = commonNames.map { it.language }
        require(langs.size == langs.toSet().size) { "duplicate language in common names" }
    }
}
