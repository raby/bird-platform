package com.digitalbluebird.species.adapter.outbound.elasticsearch

import com.digitalbluebird.shared.domain.SpeciesId
import com.digitalbluebird.species.domain.CommonName
import com.digitalbluebird.species.domain.ConservationStatus
import com.digitalbluebird.species.domain.Species
import com.digitalbluebird.species.domain.TaxonomicClassification

internal data class SpeciesDocument(
    val id: String,
    val scientificName: String,
    val commonNames: List<CommonNameDocument>,
    val taxonomy: TaxonomyDocument,
    val conservationStatus: String,
) {
    fun toDomain(): Species = Species(
        id = SpeciesId(id),
        scientificName = scientificName,
        commonNames = commonNames.map { CommonName(it.language, it.name) },
        taxonomy = TaxonomicClassification(
            kingdom = taxonomy.kingdom,
            phylum = taxonomy.phylum,
            className = taxonomy.className,
            order = taxonomy.order,
            family = taxonomy.family,
            genus = taxonomy.genus,
        ),
        conservationStatus = ConservationStatus.valueOf(conservationStatus),
    )

    companion object {
        fun from(s: Species): SpeciesDocument = SpeciesDocument(
            id = s.id.value,
            scientificName = s.scientificName,
            commonNames = s.commonNames.map { CommonNameDocument(it.language, it.name) },
            taxonomy = TaxonomyDocument(
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

internal data class CommonNameDocument(val language: String, val name: String)

internal data class TaxonomyDocument(
    val kingdom: String,
    val phylum: String,
    val className: String,
    val order: String,
    val family: String,
    val genus: String,
)
