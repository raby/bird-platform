package com.digitalbluebird.species.adapter.outbound.memory

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.digitalbluebird.shared.domain.SpeciesId
import com.digitalbluebird.species.domain.CommonName
import com.digitalbluebird.species.domain.ConservationStatus
import com.digitalbluebird.species.domain.Species
import com.digitalbluebird.species.domain.TaxonomicClassification
import org.junit.jupiter.api.Test

/**
 * The `demo`-profile species read model: an in-JVM matcher standing in for Elasticsearch. It mirrors
 * the UI's standalone fallback (ui/src/mock.ts), so its ranking is pinned the same way — exact beats
 * prefix beats substring beats subsequence — and it needs no Docker.
 */
class InMemorySpeciesReadModelTest {

    private fun species(id: String, sci: String, common: String): Species = Species(
        id = SpeciesId(id),
        scientificName = sci,
        commonNames = listOf(CommonName("en", common)),
        taxonomy = TaxonomicClassification("Animalia", "Chordata", "Aves", "Order", "Family", "Genus"),
        conservationStatus = ConservationStatus.LEAST_CONCERN,
    )

    @Test
    fun `ranks exact over prefix over substring`() {
        val exact = species("a", "Aaa bbb", "Swift")
        val prefix = species("b", "Ccc ddd", "Swifter")
        val substring = species("c", "Eee fff", "Fast Swift Bird")
        val model = InMemorySpeciesReadModel().apply { indexAll(listOf(substring, prefix, exact)) }

        assertThat(model.search("swift", 20)).containsExactly(exact, prefix, substring)
    }

    @Test
    fun `matches common names case-insensitively and drops non-matches`() {
        val robin = species("erithacus-rubecula", "Erithacus rubecula", "European Robin")
        val swift = species("apus-apus", "Apus apus", "Common Swift")
        val model = InMemorySpeciesReadModel().apply { indexAll(listOf(robin, swift)) }

        assertThat(model.search("ROBIN", 20)).containsExactly(robin)
    }

    @Test
    fun `respects the limit`() {
        val model = InMemorySpeciesReadModel().apply {
            indexAll(listOf(species("a", "Aaa", "Swift one"), species("b", "Bbb", "Swift two"), species("c", "Ccc", "Swift three")))
        }

        assertThat(model.search("swift", 2).size).isEqualTo(2)
    }

    @Test
    fun `findById returns the match or null`() {
        val robin = species("erithacus-rubecula", "Erithacus rubecula", "European Robin")
        val model = InMemorySpeciesReadModel().apply { indexAll(listOf(robin)) }

        assertThat(model.findById(SpeciesId("erithacus-rubecula"))).isEqualTo(robin)
        assertThat(model.findById(SpeciesId("no-such-id"))).isNull()
    }

    @Test
    fun `an unseeded model returns nothing`() {
        assertThat(InMemorySpeciesReadModel().search("robin", 20)).isEmpty()
    }
}
