package com.digitalbluebird.expeditions.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.digitalbluebird.expeditions.domain.Expedition
import com.digitalbluebird.expeditions.domain.ExpeditionId
import com.digitalbluebird.expeditions.domain.ExpeditionLeg
import com.digitalbluebird.expeditions.domain.ExpeditionStatus
import com.digitalbluebird.expeditions.domain.LegStatus
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
class JdbcExpeditionRepositoryIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private lateinit var repository: JdbcExpeditionRepository
    private lateinit var jdbc: JdbcTemplate

    private val slotStart = Instant.parse("2026-11-01T08:00:00Z")
    private val slotEnd = Instant.parse("2026-11-01T10:00:00Z")
    private val now = Instant.parse("2026-05-01T09:00:00Z")

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker unavailable — skipping integration test")
        postgres.start()

        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
            setDriverClassName("org.postgresql.Driver")
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/expeditions").load().migrate()

        jdbc = JdbcTemplate(dataSource)
        repository = JdbcExpeditionRepository(NamedParameterJdbcTemplate(dataSource))
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE expeditions.expedition_leg, expeditions.expedition")
    }

    private fun expedition(id: ExpeditionId, status: ExpeditionStatus, legs: List<ExpeditionLeg>) =
        Expedition(id, "leader-1", 4, status, legs, now, now)

    @Test
    fun `save and findById round-trip an expedition with its legs`() {
        val id = ExpeditionId.random()
        val bookingId = UUID.randomUUID().toString()
        repository.save(
            expedition(
                id, ExpeditionStatus.BOOKING,
                listOf(
                    ExpeditionLeg("hide-A", slotStart, slotEnd, bookingId, LegStatus.BOOKED),
                    ExpeditionLeg("hide-B", slotStart, slotEnd, null, LegStatus.PENDING),
                ),
            ),
        )

        val loaded = repository.findById(id)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.status).isEqualTo(ExpeditionStatus.BOOKING)
        assertThat(loaded.legs.size).isEqualTo(2)
        assertThat(loaded.legs[0].hideId).isEqualTo("hide-A")
        assertThat(loaded.legs[0].bookingId).isEqualTo(bookingId)
        assertThat(loaded.legs[0].status).isEqualTo(LegStatus.BOOKED)
        assertThat(loaded.legs[1].bookingId).isNull()
    }

    @Test
    fun `save upserts, updating the status and legs in place`() {
        val id = ExpeditionId.random()
        val bookingId = UUID.randomUUID().toString()
        repository.save(
            expedition(id, ExpeditionStatus.BOOKING, listOf(ExpeditionLeg("hide-A", slotStart, slotEnd, bookingId, LegStatus.BOOKED))),
        )
        repository.save(
            expedition(id, ExpeditionStatus.FAILED, listOf(ExpeditionLeg("hide-A", slotStart, slotEnd, bookingId, LegStatus.CANCELLED))),
        )

        val loaded = repository.findById(id)!!
        assertThat(loaded.status).isEqualTo(ExpeditionStatus.FAILED)
        assertThat(loaded.legs.size).isEqualTo(1)
        assertThat(loaded.legs[0].status).isEqualTo(LegStatus.CANCELLED)
    }

    @Test
    fun `findByLegBookingId correlates a booking back to its expedition`() {
        val id = ExpeditionId.random()
        val bookingId = UUID.randomUUID().toString()
        repository.save(
            expedition(id, ExpeditionStatus.CONFIRMED, listOf(ExpeditionLeg("hide-A", slotStart, slotEnd, bookingId, LegStatus.BOOKED))),
        )

        assertThat(repository.findByLegBookingId(bookingId)?.id).isEqualTo(id)
        assertThat(repository.findByLegBookingId(UUID.randomUUID().toString())).isNull()
    }
}
