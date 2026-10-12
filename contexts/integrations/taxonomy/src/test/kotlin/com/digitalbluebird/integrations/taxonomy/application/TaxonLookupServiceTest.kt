package com.digitalbluebird.integrations.taxonomy.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.digitalbluebird.integrations.taxonomy.domain.CachedTaxon
import com.digitalbluebird.integrations.taxonomy.domain.Taxon
import com.digitalbluebird.integrations.taxonomy.domain.TaxonSource
import com.digitalbluebird.integrations.taxonomy.domain.TaxonomyError
import com.digitalbluebird.integrations.taxonomy.domain.port.outbound.TaxonCache
import com.digitalbluebird.integrations.taxonomy.domain.port.outbound.TaxonomyAuthority
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class TaxonLookupServiceTest {

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    private val t0 = Instant.parse("2026-05-01T09:00:00Z")
    private val clock = MutableClock(t0)
    private val cache = mockk<TaxonCache>(relaxed = true)
    private val authority = mockk<TaxonomyAuthority>()
    private val service = TaxonLookupService(cache, authority, clock, Duration.ofMinutes(10))

    private val blackbird = Taxon("Turdus merula", "Common Blackbird", "Turdidae", "Passeriformes", "LEAST_CONCERN")

    @Test
    fun `a fresh cache hit is returned without touching the authority`() {
        every { cache.get("Turdus merula") } returns CachedTaxon(blackbird, t0)
        clock.now = t0.plusSeconds(60) // within the 10-minute TTL

        val result = service.lookup("Turdus merula").getOrNull()!!
        assertThat(result.source).isEqualTo(TaxonSource.CACHE_FRESH)
        assertThat(result.taxon).isEqualTo(blackbird)
        verify(exactly = 0) { authority.lookup(any()) }
    }

    @Test
    fun `a cache miss fetches from the authority and caches it`() {
        every { cache.get("Turdus merula") } returns null
        every { authority.lookup("Turdus merula") } returns blackbird

        val result = service.lookup("Turdus merula").getOrNull()!!
        assertThat(result.source).isEqualTo(TaxonSource.FETCHED)
        verify(exactly = 1) { cache.put(blackbird, t0) }
    }

    @Test
    fun `an expired entry is re-fetched`() {
        every { cache.get("Turdus merula") } returns CachedTaxon(blackbird, t0)
        every { authority.lookup("Turdus merula") } returns blackbird
        clock.now = t0.plus(Duration.ofMinutes(11)) // past the TTL

        assertThat(service.lookup("Turdus merula").getOrNull()!!.source).isEqualTo(TaxonSource.FETCHED)
    }

    @Test
    fun `when the authority is down, an expired entry is served stale`() {
        every { cache.get("Turdus merula") } returns CachedTaxon(blackbird, t0)
        every { authority.lookup("Turdus merula") } throws RuntimeException("offline")
        clock.now = t0.plus(Duration.ofMinutes(11)) // expired

        val result = service.lookup("Turdus merula").getOrNull()!!
        assertThat(result.source).isEqualTo(TaxonSource.STALE_FALLBACK)
        assertThat(result.taxon).isEqualTo(blackbird)
    }

    @Test
    fun `when the authority is down and nothing is cached, the lookup fails`() {
        every { cache.get("Turdus merula") } returns null
        every { authority.lookup("Turdus merula") } throws RuntimeException("offline")

        assertThat(service.lookup("Turdus merula").leftOrNull()!!)
            .isInstanceOf(TaxonomyError.AuthorityUnavailable::class)
    }

    @Test
    fun `an unknown taxon (authority reachable) is not found`() {
        every { cache.get("Nonexistent species") } returns null
        every { authority.lookup("Nonexistent species") } returns null

        assertThat(service.lookup("Nonexistent species").leftOrNull()!!)
            .isInstanceOf(TaxonomyError.TaxonNotFound::class)
    }

    @Test
    fun `a blank name is rejected`() {
        assertThat(service.lookup("   ").leftOrNull()!!).isInstanceOf(TaxonomyError.InvalidName::class)
    }
}
