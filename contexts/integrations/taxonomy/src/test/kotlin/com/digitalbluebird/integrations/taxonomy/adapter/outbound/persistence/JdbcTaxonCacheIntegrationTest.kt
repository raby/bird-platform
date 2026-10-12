package com.digitalbluebird.integrations.taxonomy.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.digitalbluebird.integrations.taxonomy.domain.Taxon
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcTaxonCacheIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private lateinit var cache: JdbcTaxonCache
    private lateinit var jdbc: JdbcTemplate

    private val blackbird = Taxon("Turdus merula", "Common Blackbird", "Turdidae", "Passeriformes", "LEAST_CONCERN")

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker unavailable — skipping integration test")
        postgres.start()

        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
            setDriverClassName("org.postgresql.Driver")
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/taxonomy").load().migrate()

        jdbc = JdbcTemplate(dataSource)
        cache = JdbcTaxonCache(NamedParameterJdbcTemplate(dataSource))
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE taxonomy.taxon_cache")
    }

    @Test
    fun `put then get round-trips a cached taxon`() {
        val at = Instant.parse("2026-05-01T09:00:00Z")
        cache.put(blackbird, at)

        val got = cache.get("Turdus merula")!!
        assertThat(got.taxon).isEqualTo(blackbird)
        assertThat(got.fetchedAt).isEqualTo(at)
        assertThat(cache.get("Unknown species")).isNull()
    }

    @Test
    fun `put upserts, refreshing the row and its fetched_at`() {
        cache.put(blackbird, Instant.parse("2026-05-01T09:00:00Z"))
        val later = Instant.parse("2026-05-01T12:00:00Z")
        cache.put(blackbird.copy(conservationStatus = "NEAR_THREATENED"), later)

        val got = cache.get("Turdus merula")!!
        assertThat(got.taxon.conservationStatus).isEqualTo("NEAR_THREATENED")
        assertThat(got.fetchedAt).isEqualTo(later)
    }
}
