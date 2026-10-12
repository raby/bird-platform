package com.digitalbluebird.integrations.taxonomy.config

import com.digitalbluebird.integrations.taxonomy.adapter.outbound.authority.StubTaxonomyAuthority
import com.digitalbluebird.integrations.taxonomy.adapter.outbound.persistence.JdbcTaxonCache
import com.digitalbluebird.integrations.taxonomy.application.TaxonLookupService
import com.digitalbluebird.integrations.taxonomy.domain.port.outbound.TaxonCache
import com.digitalbluebird.integrations.taxonomy.domain.port.outbound.TaxonomyAuthority
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock
import java.time.Duration

@Configuration
class TaxonomyConfiguration {

    @Bean
    fun taxonCache(jdbc: NamedParameterJdbcTemplate): TaxonCache = JdbcTaxonCache(jdbc)

    // Concrete type so the controller's demo offline-toggle can inject it, while the service injects it
    // through the TaxonomyAuthority port — one bean satisfies both.
    @Bean
    fun taxonomyAuthority(): StubTaxonomyAuthority = StubTaxonomyAuthority()

    @Bean
    fun taxonLookupService(
        cache: TaxonCache,
        authority: TaxonomyAuthority,
        clock: Clock,
        @Value("\${bird.taxonomy.ttl-seconds:3600}") ttlSeconds: Long,
    ): TaxonLookupService = TaxonLookupService(cache, authority, clock, Duration.ofSeconds(ttlSeconds))
}
