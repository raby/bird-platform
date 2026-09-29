package com.digitalbluebird.species.adapter.outbound.memory

import com.digitalbluebird.shared.domain.SpeciesId
import com.digitalbluebird.species.domain.Species
import com.digitalbluebird.species.domain.port.outbound.SpeciesReadModel

/**
 * In-JVM [SpeciesReadModel] for the `demo` profile: holds the imported species in memory and ranks
 * them with a cheap local matcher, so the whole app runs on Postgres alone with no Elasticsearch to
 * stand up. It is the backend twin of the UI's standalone fallback (ui/src/mock.ts) — same five
 * species, same scoring — so search behaves identically whether the SPA talks to this demo backend
 * or runs on its own. The only fidelity trade versus [com.digitalbluebird.species.adapter.outbound
 * .elasticsearch.ElasticsearchSpeciesReadModel] is fuzzy ranking: production uses Elasticsearch's
 * `multi_match` with `fuzziness(AUTO)`; here a scored substring/subsequence match approximates it.
 *
 * [indexAll] is called once at startup (the demo seeder) before any request; the snapshot is held in
 * a `@Volatile` reference so a later re-index publishes atomically to concurrent readers.
 */
class InMemorySpeciesReadModel : SpeciesReadModel {

    @Volatile
    private var snapshot: List<Species> = emptyList()

    override fun indexAll(species: List<Species>) {
        snapshot = species.toList()
    }

    override fun search(query: String, limit: Int): List<Species> =
        snapshot
            .map { it to bestScore(it, query) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

    override fun findById(id: SpeciesId): Species? = snapshot.firstOrNull { it.id == id }

    /** The best match across the scientific name and every common name. */
    private fun bestScore(species: Species, needle: String): Int =
        (sequenceOf(species.scientificName) + species.commonNames.asSequence().map { it.name })
            .maxOf { score(it, needle) }

    /** Cheap relevance: exact = 4, prefix = 3, substring = 2, in-order subsequence = 1, else 0. */
    private fun score(haystack: String, needle: String): Int {
        val h = haystack.lowercase()
        val n = needle.lowercase()
        return when {
            h == n -> 4
            h.startsWith(n) -> 3
            h.contains(n) -> 2
            isSubsequence(n, h) -> 1
            else -> 0
        }
    }

    /** True when every char of [needle] appears in [haystack] in order (not necessarily adjacent). */
    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var i = 0
        for (ch in haystack) if (i < needle.length && ch == needle[i]) i++
        return i == needle.length
    }
}
