package com.digitalbluebird.integrations.taxonomy.domain.port.outbound

import com.digitalbluebird.integrations.taxonomy.domain.CachedTaxon
import com.digitalbluebird.integrations.taxonomy.domain.Taxon
import java.time.Instant

interface TaxonCache {
    fun get(scientificName: String): CachedTaxon?

    /** Store (or refresh) a taxon, stamped with when it was fetched. */
    fun put(taxon: Taxon, fetchedAt: Instant)
}
