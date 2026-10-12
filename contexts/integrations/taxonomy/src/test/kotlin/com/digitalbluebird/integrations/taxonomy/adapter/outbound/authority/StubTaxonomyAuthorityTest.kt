package com.digitalbluebird.integrations.taxonomy.adapter.outbound.authority

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StubTaxonomyAuthorityTest {

    private val authority = StubTaxonomyAuthority()

    @Test
    fun `returns a known taxon`() {
        assertThat(authority.lookup("Turdus merula")?.commonName).isEqualTo("Common Blackbird")
    }

    @Test
    fun `returns null for an unknown taxon`() {
        assertThat(authority.lookup("Nonexistent species")).isNull()
    }

    @Test
    fun `throws when offline`() {
        authority.setOffline(true)
        assertThrows<TaxonomyAuthorityUnavailableException> { authority.lookup("Turdus merula") }
    }
}
