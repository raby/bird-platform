package com.digitalbluebird.integrations.taxonomy.domain.port.outbound

import com.digitalbluebird.integrations.taxonomy.domain.Taxon

/**
 * The external taxonomy authority (Avibase, the IOC World Bird List, …). Returns the taxon, or null if
 * the authority is reachable but holds no such taxon. Throws if the authority is unreachable — the
 * service then falls back to a stale cached value if it has one.
 */
fun interface TaxonomyAuthority {
    fun lookup(scientificName: String): Taxon?
}
