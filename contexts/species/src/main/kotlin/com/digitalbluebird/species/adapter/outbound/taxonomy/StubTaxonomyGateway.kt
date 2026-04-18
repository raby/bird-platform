package com.digitalbluebird.species.adapter.outbound.taxonomy

import com.digitalbluebird.shared.domain.SpeciesId
import com.digitalbluebird.species.domain.CommonName
import com.digitalbluebird.species.domain.ConservationStatus
import com.digitalbluebird.species.domain.Species
import com.digitalbluebird.species.domain.TaxonomicClassification
import com.digitalbluebird.species.domain.port.outbound.TaxonomyGateway

class StubTaxonomyGateway : TaxonomyGateway {

    override fun fetchSpecies(): List<Species> = EXTERNAL_DATA.map(::translate)

    private fun translate(e: ExternalSpeciesDto): Species = Species(
        id = SpeciesId(e.sci_name.lowercase().replace(" ", "-")),
        scientificName = e.sci_name,
        commonNames = listOfNotNull(
            CommonName("en", e.en_name),
            e.nl_name?.let { CommonName("nl", it) },
        ),
        taxonomy = TaxonomicClassification(
            kingdom = "Animalia",
            phylum = "Chordata",
            className = "Aves",
            order = e.order_,
            family = e.fam,
            genus = e.gen,
        ),
        conservationStatus = translateIucn(e.iucn),
    )

    private fun translateIucn(code: String?): ConservationStatus = when (code) {
        "LC" -> ConservationStatus.LEAST_CONCERN
        "NT" -> ConservationStatus.NEAR_THREATENED
        "VU" -> ConservationStatus.VULNERABLE
        "EN" -> ConservationStatus.ENDANGERED
        "CR" -> ConservationStatus.CRITICALLY_ENDANGERED
        "EW" -> ConservationStatus.EXTINCT_IN_WILD
        "EX" -> ConservationStatus.EXTINCT
        "DD" -> ConservationStatus.DATA_DEFICIENT
        else -> ConservationStatus.NOT_EVALUATED
    }

    companion object {
        internal val EXTERNAL_DATA = listOf(
            ExternalSpeciesDto("Turdus merula", "Common Blackbird", "Merel", "Passeriformes", "Turdidae", "Turdus", "LC"),
            ExternalSpeciesDto("Erithacus rubecula", "European Robin", "Roodborst", "Passeriformes", "Muscicapidae", "Erithacus", "LC"),
            ExternalSpeciesDto("Apus apus", "Common Swift", "Gierzwaluw", "Apodiformes", "Apodidae", "Apus", "LC"),
            ExternalSpeciesDto("Falco peregrinus", "Peregrine Falcon", "Slechtvalk", "Falconiformes", "Falconidae", "Falco", "LC"),
            ExternalSpeciesDto("Streptopelia turtur", "European Turtle Dove", "Tortelduif", "Columbiformes", "Columbidae", "Streptopelia", "VU"),
        )
    }
}
