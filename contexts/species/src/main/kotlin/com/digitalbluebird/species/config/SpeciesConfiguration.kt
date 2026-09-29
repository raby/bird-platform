package com.digitalbluebird.species.config

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.digitalbluebird.species.adapter.outbound.elasticsearch.ElasticsearchSpeciesReadModel
import com.digitalbluebird.species.adapter.outbound.memory.InMemorySpeciesReadModel
import com.digitalbluebird.species.adapter.outbound.taxonomy.StubTaxonomyGateway
import com.digitalbluebird.species.application.SpeciesImportService
import com.digitalbluebird.species.application.SpeciesSearchService
import com.digitalbluebird.species.domain.port.inbound.ImportTaxonomyUseCase
import com.digitalbluebird.species.domain.port.outbound.SpeciesReadModel
import com.digitalbluebird.species.domain.port.outbound.TaxonomyGateway
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class SpeciesConfiguration {

    @Bean
    fun taxonomyGateway(): TaxonomyGateway = StubTaxonomyGateway()

    // Production/default: the species read model is backed by Elasticsearch (fuzzy `multi_match`).
    @Bean
    @Profile("!demo")
    fun speciesReadModel(client: ElasticsearchClient): SpeciesReadModel =
        ElasticsearchSpeciesReadModel(client)

    // `demo` profile: an in-JVM read model so the app runs on Postgres alone (no Elasticsearch to
    // stand up). Exactly one SpeciesReadModel is active per profile, so the port injection below stays
    // unambiguous — SpeciesWiringTest's demo-profile case guards that this variant boots with no
    // ElasticsearchClient.
    @Bean
    @Profile("demo")
    fun inMemorySpeciesReadModel(): SpeciesReadModel = InMemorySpeciesReadModel()

    // `demo` profile: seed the read model on startup through the real import path (the same
    // StubTaxonomyGateway + SpeciesImportService the `POST /species/import` endpoint drives), so
    // species search returns results the moment the demo boots.
    @Bean
    @Profile("demo")
    fun speciesDemoSeeder(importTaxonomy: ImportTaxonomyUseCase): ApplicationRunner =
        ApplicationRunner { importTaxonomy.importAll() }

    // SpeciesController injects the inbound ports (ImportTaxonomyUseCase, SearchSpeciesUseCase); each
    // resolves to its single concrete service bean below. A second, interface-typed bean for the same
    // instance would make that port injection ambiguous (NoUniqueBeanDefinitionException at startup).
    @Bean
    fun speciesImportService(gateway: TaxonomyGateway, readModel: SpeciesReadModel): SpeciesImportService =
        SpeciesImportService(gateway, readModel)

    @Bean
    fun speciesSearchService(readModel: SpeciesReadModel): SpeciesSearchService =
        SpeciesSearchService(readModel)
}
