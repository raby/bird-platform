package com.digitalbluebird.integrations.taxonomy.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.digitalbluebird.integrations.taxonomy.domain.TaxonResult
import com.digitalbluebird.integrations.taxonomy.domain.TaxonSource
import com.digitalbluebird.integrations.taxonomy.domain.TaxonomyError
import com.digitalbluebird.integrations.taxonomy.domain.port.inbound.LookupTaxonUseCase
import com.digitalbluebird.integrations.taxonomy.domain.port.outbound.TaxonCache
import com.digitalbluebird.integrations.taxonomy.domain.port.outbound.TaxonomyAuthority
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Read-through cache over the taxonomy authority. Taxonomy is near-static reference data behind a
 * rate-limited external API, so lookups are cached:
 *
 *  - a cache hit within [ttl] is returned straight away (CACHE_FRESH);
 *  - a miss or an expired entry is fetched from the authority and cached (FETCHED);
 *  - if the authority is unreachable, an expired cached value is served anyway (STALE_FALLBACK) —
 *    graceful degradation: a stale answer beats no answer for reference data.
 *
 * Only when there is no cached value and the authority is down does the lookup fail.
 *
 * Known limitation: on expiry, concurrent lookups for the same taxon all miss and all fetch at once (a
 * cache stampede against the very dependency the cache protects). Single-flight / request coalescing —
 * one in-flight fetch per key, the rest awaiting it — would close that; it is out of scope here.
 */
class TaxonLookupService(
    private val cache: TaxonCache,
    private val authority: TaxonomyAuthority,
    private val clock: Clock,
    private val ttl: Duration,
) : LookupTaxonUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun lookup(scientificName: String): Either<TaxonomyError, TaxonResult> {
        val name = scientificName.trim()
        if (name.isBlank()) return TaxonomyError.InvalidName("scientificName must not be blank").left()

        val cached = cache.get(name)
        if (cached != null && isFresh(cached.fetchedAt)) {
            return TaxonResult(cached.taxon, TaxonSource.CACHE_FRESH, cached.fetchedAt).right()
        }

        val fetched = try {
            authority.lookup(name)
        } catch (e: Exception) {
            // Authority unreachable or otherwise failing: serve the stale cached value if we have one,
            // else fail. Serving a stale answer beats no answer for near-static reference data.
            return if (cached != null) {
                log.warn("taxonomy authority unreachable for {}; serving stale cache", name, e)
                TaxonResult(cached.taxon, TaxonSource.STALE_FALLBACK, cached.fetchedAt).right()
            } else {
                TaxonomyError.AuthorityUnavailable(name).left()
            }
        }

        return if (fetched != null) {
            val now = clock.instant()
            cache.put(fetched, now)
            TaxonResult(fetched, TaxonSource.FETCHED, now).right()
        } else {
            // Authority is reachable and has no such taxon.
            TaxonomyError.TaxonNotFound(name).left()
        }
    }

    private fun isFresh(fetchedAt: Instant): Boolean = !fetchedAt.plus(ttl).isBefore(clock.instant())
}
