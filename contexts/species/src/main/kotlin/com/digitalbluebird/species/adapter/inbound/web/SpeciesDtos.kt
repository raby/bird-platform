package com.digitalbluebird.species.adapter.inbound.web

import com.digitalbluebird.species.domain.Species

data class SpeciesResponse(
    val id: String,
    val scientificName: String,
    val commonNames: List<CommonNameResponse>,
    val taxonomy: TaxonomyResponse,
    val conservationStatus: String,
) {
    companion object {
        fun from(s: Species): SpeciesResponse = SpeciesResponse(
            id = s.id.value,
            scientificName = s.scientificName,
            commonNames = s.commonNames.map { CommonNameResponse(it.language, it.name) },
            taxonomy = TaxonomyResponse(
                kingdom = s.taxonomy.kingdom,
                phylum = s.taxonomy.phylum,
                className = s.taxonomy.className,
                order = s.taxonomy.order,
                family = s.taxonomy.family,
                genus = s.taxonomy.genus,
            ),
            conservationStatus = s.conservationStatus.name,
        )
    }
}

data class CommonNameResponse(val language: String, val name: String)

data class TaxonomyResponse(
    val kingdom: String,
    val phylum: String,
    val className: String,
    val order: String,
    val family: String,
    val genus: String,
)

data class ImportResponse(val imported: Int)

data class ErrorResponse(val code: String, val message: String)
