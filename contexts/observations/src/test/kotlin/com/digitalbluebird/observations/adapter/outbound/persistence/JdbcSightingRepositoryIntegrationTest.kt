package com.digitalbluebird.observations.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.digitalbluebird.observations.domain.Count
import com.digitalbluebird.observations.domain.Sighting
import com.digitalbluebird.observations.domain.SightingId
import com.digitalbluebird.shared.domain.Location
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.shared.domain.SpeciesId
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
class JdbcSightingRepositoryIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private lateinit var repository: JdbcSightingRepository
    private lateinit var jdbc: JdbcTemplate

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker unavailable — skipping integration test")
        postgres.start()

        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
            setDriverClassName("org.postgresql.Driver")
        }

        Flyway.configure()
            .dataSource(dataSource)
            .locations(
                "classpath:db/migration/shared",
                "classpath:db/migration/observations",
            )
            .load()
            .migrate()

        jdbc = JdbcTemplate(dataSource)
        repository = JdbcSightingRepository(NamedParameterJdbcTemplate(dataSource))
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE observations.sightings")
    }

    @Test
    fun `save persists sighting and findById round-trips all fields`() {
        val sighting = Sighting(
            id = SightingId.random(),
            observerId = ObserverId.random(),
            speciesId = SpeciesId("apus-apus"),
            location = Location.of(51.5074, -0.1278),
            observedAt = Instant.parse("2026-04-17T08:15:00Z"),
            count = Count(12),
            notes = "low over the reservoir",
            createdAt = Instant.parse("2026-04-18T12:00:00Z"),
        )

        repository.save(sighting)
        val loaded = repository.findById(sighting.id)

        assertThat(loaded).isNotNull()
        assertThat(loaded).isEqualTo(sighting)
    }

    @Test
    fun `findById returns null when absent`() {
        assertThat(repository.findById(SightingId.random())).isNull()
    }

    @Test
    fun `save accepts null notes`() {
        val sighting = Sighting(
            id = SightingId.random(),
            observerId = ObserverId.random(),
            speciesId = SpeciesId("erithacus-rubecula"),
            location = Location.of(52.0, 0.0),
            observedAt = Instant.parse("2026-04-17T08:15:00Z"),
            count = Count(1),
            notes = null,
            createdAt = Instant.parse("2026-04-18T12:00:00Z"),
        )
        repository.save(sighting)
        assertThat(repository.findById(sighting.id)!!.notes).isNull()
    }
}
