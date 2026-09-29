package com.digitalbluebird.species.config

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.digitalbluebird.species.adapter.outbound.elasticsearch.ElasticsearchSpeciesReadModel
import com.digitalbluebird.species.adapter.outbound.taxonomy.StubTaxonomyGateway
import com.digitalbluebird.species.application.SpeciesImportService
import com.digitalbluebird.species.application.SpeciesSearchService
import com.digitalbluebird.species.domain.port.outbound.SpeciesReadModel
import com.digitalbluebird.species.domain.port.outbound.TaxonomyGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpeciesConfiguration {

    @Bean
    fun taxonomyGateway(): TaxonomyGateway = StubTaxonomyGateway()

    @Bean
    fun speciesReadModel(client: ElasticsearchClient): SpeciesReadModel =
        ElasticsearchSpeciesReadModel(client)

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
