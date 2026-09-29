package com.digitalbluebird.species.config

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.digitalbluebird.species.adapter.outbound.memory.InMemorySpeciesReadModel
import com.digitalbluebird.species.domain.port.inbound.ImportTaxonomyUseCase
import com.digitalbluebird.species.domain.port.inbound.SearchSpeciesUseCase
import com.digitalbluebird.species.domain.port.outbound.SpeciesReadModel
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier

/**
 * Guards the species wiring: each inbound port resolves to exactly one bean, so SpeciesController's
 * port injection stays unambiguous. The api-module WiringTest cannot cover species (the Elasticsearch
 * client is not on its classpath), so this lives here. No Docker needed — the client is mocked.
 */
class SpeciesWiringTest {

    private val runner = ApplicationContextRunner()
        .withBean(ElasticsearchClient::class.java, Supplier { mockk<ElasticsearchClient>() })
        .withUserConfiguration(SpeciesConfiguration::class.java)

    @Test
    fun `each species inbound port resolves to exactly one bean`() {
        runner.run { context ->
            assertThat(context.startupFailure).isNull()
            assertThat(context.getBeanNamesForType(ImportTaxonomyUseCase::class.java).toList()).hasSize(1)
            assertThat(context.getBeanNamesForType(SearchSpeciesUseCase::class.java).toList()).hasSize(1)
        }
    }

    @Test
    fun `demo profile resolves species ports without an Elasticsearch client`() {
        // No ElasticsearchClient bean: the demo profile must wire the in-JVM read model instead, and
        // still resolve each inbound port to exactly one bean. This is what lets the demo container
        // boot on Postgres alone.
        ApplicationContextRunner()
            .withInitializer { it.environment.setActiveProfiles("demo") }
            .withUserConfiguration(SpeciesConfiguration::class.java)
            .run { context ->
                assertThat(context.startupFailure).isNull()
                assertThat(context.getBeanNamesForType(ImportTaxonomyUseCase::class.java).toList()).hasSize(1)
                assertThat(context.getBeanNamesForType(SearchSpeciesUseCase::class.java).toList()).hasSize(1)
                assertThat(context.getBean(SpeciesReadModel::class.java)).isInstanceOf(InMemorySpeciesReadModel::class)
            }
    }
}
