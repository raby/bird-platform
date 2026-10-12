package com.digitalbluebird.integrations.taxonomy.adapter.outbound.authority

import com.digitalbluebird.integrations.taxonomy.domain.Taxon
import com.digitalbluebird.integrations.taxonomy.domain.port.outbound.TaxonomyAuthority
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/** Thrown when the (stubbed) taxonomy authority is unreachable. */
class TaxonomyAuthorityUnavailableException(message: String) : RuntimeException(message)

/**
 * Stand-in for the external taxonomy authority (Avibase / the IOC list). A real adapter would call
 * their API; this one answers from a fixed catalogue. [setOffline] simulates the authority going down,
 * so the read-through cache's stale-fallback behaviour is demonstrable.
 */
class StubTaxonomyAuthority : TaxonomyAuthority {

    private val log = LoggerFactory.getLogger(javaClass)
    private val offline = AtomicBoolean(false)

    /** Demo control: pretend the authority is (un)reachable. */
    fun setOffline(value: Boolean) = offline.set(value)

    override fun lookup(scientificName: String): Taxon? {
        if (offline.get()) throw TaxonomyAuthorityUnavailableException("authority offline")
        log.info("taxonomy authority lookup: {}", scientificName)
        return CATALOGUE[scientificName]
    }

    private companion object {
        val CATALOGUE: Map<String, Taxon> = listOf(
            Taxon("Turdus merula", "Common Blackbird", "Turdidae", "Passeriformes", "LEAST_CONCERN"),
            Taxon("Erithacus rubecula", "European Robin", "Muscicapidae", "Passeriformes", "LEAST_CONCERN"),
            Taxon("Apus apus", "Common Swift", "Apodidae", "Apodiformes", "LEAST_CONCERN"),
            Taxon("Falco peregrinus", "Peregrine Falcon", "Falconidae", "Falconiformes", "LEAST_CONCERN"),
            Taxon("Streptopelia turtur", "European Turtle Dove", "Columbidae", "Columbiformes", "VULNERABLE"),
            Taxon("Alcedo atthis", "Common Kingfisher", "Alcedinidae", "Coraciiformes", "LEAST_CONCERN"),
            Taxon("Pandion haliaetus", "Western Osprey", "Pandionidae", "Accipitriformes", "LEAST_CONCERN"),
            Taxon("Botaurus stellaris", "Eurasian Bittern", "Ardeidae", "Pelecaniformes", "LEAST_CONCERN"),
            Taxon("Tyto alba", "Barn Owl", "Tytonidae", "Strigiformes", "LEAST_CONCERN"),
            Taxon("Curruca undata", "Dartford Warbler", "Sylviidae", "Passeriformes", "NEAR_THREATENED"),
            Taxon("Panurus biarmicus", "Bearded Reedling", "Panuridae", "Passeriformes", "LEAST_CONCERN"),
        ).associateBy { it.scientificName }
    }
}
