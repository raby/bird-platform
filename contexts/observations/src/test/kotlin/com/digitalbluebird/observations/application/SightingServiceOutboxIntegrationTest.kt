package com.digitalbluebird.observations.application

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.digitalbluebird.observations.adapter.outbound.persistence.JdbcSightingRepository
import com.digitalbluebird.observations.domain.event.SightingCreatedEvent
import com.digitalbluebird.observations.domain.port.inbound.RecordSightingCommand
import com.digitalbluebird.shared.infra.outbox.JdbcOutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SightingServiceOutboxIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private val fixedInstant: Instant = Instant.parse("2026-04-18T12:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private lateinit var service: SightingService
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
            .locations("classpath:db/migration/shared", "classpath:db/migration/observations")
            .load()
            .migrate()

        val named = NamedParameterJdbcTemplate(dataSource)
        jdbc = JdbcTemplate(dataSource)
        service = SightingService(
            sightings = JdbcSightingRepository(named),
            outbox = JdbcOutboxRepository(named),
            clock = clock,
            objectMapper = objectMapper,
        )
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE observations.sightings")
        jdbc.execute("TRUNCATE TABLE shared_infra.outbox")
    }

    @Test
    fun `recording a sighting writes both the sighting row and an outbox entry`() {
        val observerId = UUID.randomUUID()
        val result = service.record(
            RecordSightingCommand(
                observerId = observerId.toString(),
                speciesId = "apus-apus",
                latitude = 51.5074,
                longitude = -0.1278,
                observedAt = fixedInstant.minusSeconds(60),
                count = 12,
                notes = "low over the reservoir",
            ),
        )
        assertThat(result.isRight()).isTrue()
        val sightingId = result.getOrNull()!!.id.value

        val sightingCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM observations.sightings WHERE id = ?",
            Long::class.java,
            sightingId,
        )
        assertThat(sightingCount).isEqualTo(1L)

        val outbox = jdbc.queryForList(
            "SELECT aggregate_type, aggregate_id, event_type, payload::text AS payload, published_at FROM shared_infra.outbox",
        )
        assertThat(outbox).hasSize(1)
        val row = outbox.single()
        assertThat(row["aggregate_type"] as String).isEqualTo("Sighting")
        assertThat(row["aggregate_id"] as String).isEqualTo(sightingId.toString())
        assertThat(row["event_type"] as String).isEqualTo("SightingCreated")
        assertThat(row["published_at"]).isEqualTo(null)

        val event = objectMapper.readValue(row["payload"] as String, SightingCreatedEvent::class.java)
        assertThat(event.sightingId).isEqualTo(sightingId.toString())
        assertThat(event.observerId).isEqualTo(observerId.toString())
        assertThat(event.speciesId).isEqualTo("apus-apus")
        assertThat(event.count).isEqualTo(12)
    }

    @Test
    fun `invalid command writes nothing`() {
        service.record(
            RecordSightingCommand(
                observerId = "not-a-uuid",
                speciesId = "x",
                latitude = 0.0,
                longitude = 0.0,
                observedAt = fixedInstant,
                count = 1,
                notes = null,
            ),
        )

        val sightings = jdbc.queryForObject("SELECT COUNT(*) FROM observations.sightings", Long::class.java)
        val outbox = jdbc.queryForObject("SELECT COUNT(*) FROM shared_infra.outbox", Long::class.java)
        assertThat(listOf(sightings, outbox)).containsExactly(0L, 0L)
    }
}
