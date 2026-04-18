package com.digitalbluebird.species.adapter.outbound.taxonomy

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import com.digitalbluebird.species.domain.ConservationStatus
import org.junit.jupiter.api.Test

class StubTaxonomyGatewayTest {

    private val gateway = StubTaxonomyGateway()

    @Test
    fun `fetchSpecies translates external shape into domain`() {
        val species = gateway.fetchSpecies()
        assertThat(species).isNotEmpty()

        val blackbird = species.first { it.scientificName == "Turdus merula" }
        assertThat(blackbird.id.value).isEqualTo("turdus-merula")
        assertThat(blackbird.taxonomy.kingdom).isEqualTo("Animalia")
        assertThat(blackbird.taxonomy.className).isEqualTo("Aves")
        assertThat(blackbird.taxonomy.order).isEqualTo("Passeriformes")
        assertThat(blackbird.conservationStatus).isEqualTo(ConservationStatus.LEAST_CONCERN)
        assertThat(blackbird.commonNames.map { it.language to it.name })
            .containsExactlyInAnyOrder("en" to "Common Blackbird", "nl" to "Merel")
    }

    @Test
    fun `vulnerable IUCN code maps to VULNERABLE`() {
        val turtleDove = gateway.fetchSpecies().first { it.scientificName == "Streptopelia turtur" }
        assertThat(turtleDove.conservationStatus).isEqualTo(ConservationStatus.VULNERABLE)
    }
}
