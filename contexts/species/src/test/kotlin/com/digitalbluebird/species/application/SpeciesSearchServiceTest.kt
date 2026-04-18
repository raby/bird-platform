package com.digitalbluebird.species.application

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.digitalbluebird.shared.domain.SpeciesId
import com.digitalbluebird.species.domain.CommonName
import com.digitalbluebird.species.domain.ConservationStatus
import com.digitalbluebird.species.domain.Species
import com.digitalbluebird.species.domain.SpeciesError
import com.digitalbluebird.species.domain.TaxonomicClassification
import com.digitalbluebird.species.domain.port.outbound.SpeciesReadModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SpeciesSearchServiceTest {

    private val readModel: SpeciesReadModel = mockk()
    private val service = SpeciesSearchService(readModel)

    private val robin = Species(
        id = SpeciesId("erithacus-rubecula"),
        scientificName = "Erithacus rubecula",
        commonNames = listOf(CommonName("en", "European Robin")),
        taxonomy = TaxonomicClassification("Animalia", "Chordata", "Aves", "Passeriformes", "Muscicapidae", "Erithacus"),
        conservationStatus = ConservationStatus.LEAST_CONCERN,
    )

    @Test
    fun `search trims and forwards to read model`() {
        every { readModel.search("robin", 20) } returns listOf(robin)

        val result = service.search("  robin  ", 20)

        assertThat(result.getOrNull()!!).isEqualTo(listOf(robin))
        verify { readModel.search("robin", 20) }
    }

    @Test
    fun `search rejects queries shorter than 2 characters`() {
        val result = service.search("a", 20)
        assertThat(result.leftOrNull()!!).isInstanceOf(SpeciesError.InvalidQuery::class)
        verify(exactly = 0) { readModel.search(any(), any()) }
    }

    @Test
    fun `search caps limit to 100`() {
        every { readModel.search("xx", 100) } returns emptyList()
        val result = service.search("xx", 5000)
        assertThat(result.getOrNull()!!).isEmpty()
        verify { readModel.search("xx", 100) }
    }
}
