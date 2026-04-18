package com.digitalbluebird.species.application

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.digitalbluebird.shared.domain.SpeciesId
import com.digitalbluebird.species.domain.CommonName
import com.digitalbluebird.species.domain.ConservationStatus
import com.digitalbluebird.species.domain.Species
import com.digitalbluebird.species.domain.SpeciesError
import com.digitalbluebird.species.domain.TaxonomicClassification
import com.digitalbluebird.species.domain.port.outbound.SpeciesReadModel
import com.digitalbluebird.species.domain.port.outbound.TaxonomyGateway
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test

class SpeciesImportServiceTest {

    private val gateway: TaxonomyGateway = mockk()
    private val readModel: SpeciesReadModel = mockk(relaxed = true)
    private val service = SpeciesImportService(gateway, readModel)

    private val sampleSpecies = Species(
        id = SpeciesId("turdus-merula"),
        scientificName = "Turdus merula",
        commonNames = listOf(CommonName("en", "Common Blackbird")),
        taxonomy = TaxonomicClassification("Animalia", "Chordata", "Aves", "Passeriformes", "Turdidae", "Turdus"),
        conservationStatus = ConservationStatus.LEAST_CONCERN,
    )

    @Test
    fun `importAll fetches from gateway and forwards to read model`() {
        every { gateway.fetchSpecies() } returns listOf(sampleSpecies)
        val captured = slot<List<Species>>()
        every { readModel.indexAll(capture(captured)) } answers { }

        val result = service.importAll()

        assertThat(result.isRight()).isTrue()
        assertThat(result.getOrNull()!!.imported).isEqualTo(1)
        assertThat(captured.captured).containsExactly(sampleSpecies)
        verify(exactly = 1) { readModel.indexAll(any()) }
    }

    @Test
    fun `importAll returns TaxonomyImportFailed when gateway throws`() {
        every { gateway.fetchSpecies() } throws RuntimeException("upstream 503")

        val result = service.importAll()

        assertThat(result.leftOrNull()!!).isInstanceOf(SpeciesError.TaxonomyImportFailed::class)
        verify(exactly = 0) { readModel.indexAll(any()) }
    }

    @Test
    fun `importAll reports zero when gateway returns empty list`() {
        every { gateway.fetchSpecies() } returns emptyList()

        val result = service.importAll()

        assertThat(result.getOrNull()!!.imported).isEqualTo(0)
    }
}
