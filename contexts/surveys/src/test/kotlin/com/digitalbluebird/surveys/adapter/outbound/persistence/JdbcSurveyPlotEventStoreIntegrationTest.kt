package com.digitalbluebird.surveys.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.digitalbluebird.surveys.domain.SurveyPlotId
import com.digitalbluebird.surveys.domain.event.PlotClaimed
import com.digitalbluebird.surveys.domain.event.PlotReleased
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
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSurveyPlotEventStoreIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private lateinit var store: JdbcSurveyPlotEventStore
    private lateinit var jdbc: JdbcTemplate
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private val plotId = SurveyPlotId(UUID.fromString("a0000000-0000-0000-0000-000000000001"))
    private val observer = UUID.randomUUID().toString()
    private val now: Instant = Instant.parse("2026-05-01T09:00:00Z")

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker unavailable — skipping integration test")
        postgres.start()
        val ds = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
            setDriverClassName("org.postgresql.Driver")
        }
        Flyway.configure().dataSource(ds).locations("classpath:db/migration/surveys").load().migrate()
        jdbc = JdbcTemplate(ds)
        store = JdbcSurveyPlotEventStore(NamedParameterJdbcTemplate(ds), objectMapper)
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE surveys.survey_plot_events")
    }

    @Test
    fun `append then load round-trips events in stream order`() {
        assertThat(store.append(plotId, 0, PlotClaimed(plotId.value.toString(), observer, 2026, now))).isTrue()
        assertThat(store.append(plotId, 1, PlotReleased(plotId.value.toString(), observer, now))).isTrue()

        val events = store.load(plotId)
        assertThat(events).hasSize(2)
        assertThat(events[0]).isInstanceOf(PlotClaimed::class)
        assertThat((events[0] as PlotClaimed).season).isEqualTo(2026)
        assertThat(events[1]).isInstanceOf(PlotReleased::class)
    }

    @Test
    fun `appending at a taken sequence loses the optimistic-concurrency race`() {
        assertThat(store.append(plotId, 0, PlotClaimed(plotId.value.toString(), observer, 2026, now))).isTrue()
        // A second writer that replayed the same (empty) stream tries to append at sequence 0 too.
        val other = PlotClaimed(plotId.value.toString(), UUID.randomUUID().toString(), 2026, now)
        assertThat(store.append(plotId, 0, other)).isFalse()
        assertThat(store.load(plotId)).hasSize(1)
    }

    @Test
    fun `load returns empty for a plot with no events`() {
        assertThat(store.load(SurveyPlotId(UUID.randomUUID()))).hasSize(0)
    }
}
