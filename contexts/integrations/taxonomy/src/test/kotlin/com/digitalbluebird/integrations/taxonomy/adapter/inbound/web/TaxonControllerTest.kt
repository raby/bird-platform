package com.digitalbluebird.integrations.taxonomy.adapter.inbound.web

import arrow.core.left
import arrow.core.right
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.integrations.taxonomy.adapter.outbound.authority.StubTaxonomyAuthority
import com.digitalbluebird.integrations.taxonomy.domain.Taxon
import com.digitalbluebird.integrations.taxonomy.domain.TaxonResult
import com.digitalbluebird.integrations.taxonomy.domain.TaxonSource
import com.digitalbluebird.integrations.taxonomy.domain.TaxonomyError
import com.digitalbluebird.integrations.taxonomy.domain.port.inbound.LookupTaxonUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant

class TaxonControllerTest {

    private val lookup = mockk<LookupTaxonUseCase>()
    private val controller = TaxonController(lookup, mockk<StubTaxonomyAuthority>(relaxed = true))

    private fun status(name: String): Int = controller.lookup(name).statusCode.value()

    @Test
    fun `a found taxon is 200`() {
        every { lookup.lookup("Turdus merula") } returns TaxonResult(
            Taxon("Turdus merula", "Common Blackbird", "Turdidae", "Passeriformes", "LEAST_CONCERN"),
            TaxonSource.FETCHED,
            Instant.parse("2026-05-01T09:00:00Z"),
        ).right()
        assertThat(status("Turdus merula")).isEqualTo(200)
    }

    @Test
    fun `an unknown taxon is 404`() {
        every { lookup.lookup("Nonexistent") } returns TaxonomyError.TaxonNotFound("Nonexistent").left()
        assertThat(status("Nonexistent")).isEqualTo(404)
    }

    @Test
    fun `a blank name is 400`() {
        every { lookup.lookup(" ") } returns TaxonomyError.InvalidName("blank").left()
        assertThat(status(" ")).isEqualTo(400)
    }

    @Test
    fun `an unavailable authority is 503`() {
        every { lookup.lookup("Turdus merula") } returns TaxonomyError.AuthorityUnavailable("Turdus merula").left()
        assertThat(status("Turdus merula")).isEqualTo(503)
    }
}
