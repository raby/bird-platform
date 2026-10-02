package com.digitalbluebird.permits.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.digitalbluebird.permits.domain.port.outbound.ReserveOutcome
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
import java.time.LocalDate
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcVisitorPermitRepositoryIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private lateinit var repository: JdbcVisitorPermitRepository
    private lateinit var jdbc: JdbcTemplate

    private val hide = UUID.fromString("10000000-0000-0000-0000-0000000000aa")
    private val uncappedHide = UUID.fromString("10000000-0000-0000-0000-0000000000bb")
    private val day: LocalDate = LocalDate.of(2026, 5, 1)

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker unavailable — skipping integration test")
        postgres.start()

        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
            setDriverClassName("org.postgresql.Driver")
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/permits").load().migrate()

        jdbc = JdbcTemplate(dataSource)
        repository = JdbcVisitorPermitRepository(NamedParameterJdbcTemplate(dataSource))
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE permits.visitor_permits, permits.permit_issuance")
        // A cap of 6 visitors on the test hide for the test day.
        jdbc.update(
            "INSERT INTO permits.visitor_permits (hide_id, day, capacity, issued) VALUES (?, ?, 6, 0)",
            hide,
            day,
        )
    }

    @Test
    fun `reserve increments within the cap and refuses what would exceed it`() {
        assertThat(repository.reserve(hide, day, 4)).isEqualTo(ReserveOutcome.RESERVED) // 0 -> 4
        assertThat(repository.reserve(hide, day, 4)).isEqualTo(ReserveOutcome.EXHAUSTED) // 4 + 4 > 6
        assertThat(repository.reserve(hide, day, 2)).isEqualTo(ReserveOutcome.RESERVED) // 4 -> 6 (exactly full)
        assertThat(repository.reserve(hide, day, 1)).isEqualTo(ReserveOutcome.EXHAUSTED) // 6 + 1 > 6

        val issued = jdbc.queryForObject(
            "SELECT issued FROM permits.visitor_permits WHERE hide_id = ? AND day = ?",
            Int::class.java,
            hide,
            day,
        )
        assertThat(issued).isEqualTo(6)
    }

    @Test
    fun `reserve reports NO_CAP for a hide and day with no configured quota`() {
        assertThat(repository.reserve(uncappedHide, day, 10)).isEqualTo(ReserveOutcome.NO_CAP)
    }

    @Test
    fun `isIssued flips once an issuance is recorded`() {
        val bookingId = UUID.randomUUID()
        assertThat(repository.isIssued(bookingId)).isFalse()

        repository.recordIssuance(bookingId, hide, day, 2, Instant.parse("2026-05-01T09:00:00Z"))

        assertThat(repository.isIssued(bookingId)).isTrue()
    }
}
