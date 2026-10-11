package com.digitalbluebird.integrations.ebird.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.digitalbluebird.integrations.ebird.domain.EbirdSubmission
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
class JdbcEbirdSubmissionRepositoryIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private lateinit var repository: JdbcEbirdSubmissionRepository
    private lateinit var jdbc: JdbcTemplate

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker unavailable — skipping integration test")
        postgres.start()

        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
            setDriverClassName("org.postgresql.Driver")
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/ebird").load().migrate()

        jdbc = JdbcTemplate(dataSource)
        repository = JdbcEbirdSubmissionRepository(NamedParameterJdbcTemplate(dataSource))
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE ebird.submission")
    }

    private fun pending(createdAt: Instant = Instant.parse("2026-05-01T09:00:00Z")) = EbirdSubmission.pending(
        sightingId = UUID.randomUUID().toString(),
        speciesId = "turdus-merula",
        latitude = 54.1,
        longitude = -2.8,
        observedAt = Instant.parse("2026-05-01T08:00:00Z"),
        count = 2,
        at = createdAt,
    )

    @Test
    fun `enqueue is idempotent on the sighting id`() {
        val submission = pending()

        assertThat(repository.enqueue(submission)).isTrue() // first wins
        assertThat(repository.enqueue(submission)).isFalse() // re-delivery deduped

        assertThat(repository.recent(10)).hasSize(1)
    }

    @Test
    fun `update moves a submission out of pending and round-trips its fields`() {
        val submission = pending()
        repository.enqueue(submission)
        assertThat(repository.findPending(10)).hasSize(1)

        repository.update(submission.submitted(Instant.parse("2026-05-01T10:00:00Z")))

        assertThat(repository.findPending(10)).hasSize(0)
        val stored = repository.recent(10).single()
        assertThat(stored.status.name).isEqualTo("SUBMITTED")
        assertThat(stored.sightingId).isEqualTo(submission.sightingId)
        assertThat(stored.speciesId).isEqualTo("turdus-merula")
        assertThat(stored.count).isEqualTo(2)
    }

    @Test
    fun `findPending returns oldest first`() {
        val older = pending(createdAt = Instant.parse("2026-05-01T08:00:00Z"))
        val newer = pending(createdAt = Instant.parse("2026-05-01T09:00:00Z"))
        repository.enqueue(newer)
        repository.enqueue(older)

        assertThat(repository.findPending(10).map { it.sightingId })
            .isEqualTo(listOf(older.sightingId, newer.sightingId))
    }
}
