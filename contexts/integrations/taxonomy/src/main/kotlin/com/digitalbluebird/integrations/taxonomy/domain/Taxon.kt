package com.digitalbluebird.integrations.taxonomy.domain

import java.time.Instant

/** A taxonomy record for a species, as held by the external authority. */
data class Taxon(
    val scientificName: String,
    val commonName: String,
    val family: String,
    val order: String,
    val conservationStatus: String,
)

/** A [Taxon] in the cache, with when it was fetched from the authority. */
data class CachedTaxon(val taxon: Taxon, val fetchedAt: Instant)

/** The outcome of a lookup, carrying where the data came from. */
data class TaxonResult(val taxon: Taxon, val source: TaxonSource, val fetchedAt: Instant)

enum class TaxonSource {
    /** A fresh cache hit, within the TTL. */
    CACHE_FRESH,

    /** Fetched from the authority (cache miss or expired entry), and cached. */
    FETCHED,

    /** The authority was unreachable, so a stale cached value was served rather than failing. */
    STALE_FALLBACK,
}
