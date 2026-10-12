package com.digitalbluebird.reserves.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import com.digitalbluebird.reserves.domain.ReserveId
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcReserveRepositoryIntegrationTest {

    // PostGIS image, declared compatible with the standard postgres container.
    private val postgres = PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private lateinit var repository: JdbcReserveRepository

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker unavailable — skipping integration test")
        postgres.start()

        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
            setDriverClassName("org.postgresql.Driver")
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/reserves").load().migrate()
        repository = JdbcReserveRepository(NamedParameterJdbcTemplate(dataSource))
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    // The search point sits on the Somerset Levels, near Ham Wall.
    private val lat = 51.15
    private val lng = -2.78

    @Test
    fun `findNearby returns reserves within the radius, nearest first`() {
        val results = repository.findNearby(lat, lng, 20.0)

        // The Somerset cluster, ordered by distance; far-flung reserves (Scotland, Suffolk, …) are excluded.
        assertThat(results.map { it.reserve.name }).containsExactly("Ham Wall", "Shapwick Heath", "Greylake")
        assertThat(results.first().distanceKm).isLessThan(1.0) // Ham Wall is essentially at the search point
        assertThat(results.map { it.distanceKm }).isEqualTo(results.map { it.distanceKm }.sorted())
    }

    @Test
    fun `findNearby excludes reserves outside the radius`() {
        assertThat(repository.findNearby(lat, lng, 1.0).map { it.reserve.name }).containsExactly("Ham Wall")
    }

    @Test
    fun `findAll returns every seeded reserve`() {
        assertThat(repository.findAll().size).isEqualTo(7)
    }

    @Test
    fun `findById returns a seeded reserve or null`() {
        val hamWall = ReserveId(UUID.fromString("20000000-0000-0000-0000-000000000003"))
        assertThat(repository.findById(hamWall)?.name).isEqualTo("Ham Wall")
        assertThat(repository.findById(ReserveId(UUID.randomUUID()))).isNull()
    }
}
