package com.digitalbluebird.notifications.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.digitalbluebird.notifications.domain.Notification
import com.digitalbluebird.notifications.domain.NotificationId
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
class JdbcNotificationRepositoryIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private lateinit var repository: JdbcNotificationRepository
    private lateinit var jdbc: JdbcTemplate

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker unavailable — skipping integration test")
        postgres.start()

        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
            setDriverClassName("org.postgresql.Driver")
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/notifications").load().migrate()

        jdbc = JdbcTemplate(dataSource)
        repository = JdbcNotificationRepository(NamedParameterJdbcTemplate(dataSource))
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE notifications.notification")
    }

    private fun pending(sourceEventId: UUID, createdAt: Instant = Instant.parse("2026-05-01T09:00:00Z")) =
        Notification.pending(
            id = NotificationId.random(),
            kind = "BOOKING_CONFIRMED",
            recipient = "observer-1",
            subject = "s",
            body = "b",
            sourceEventId = sourceEventId,
            createdAt = createdAt,
        )

    @Test
    fun `enqueue is idempotent on the source event id`() {
        val sourceEventId = UUID.randomUUID()

        assertThat(repository.enqueue(pending(sourceEventId))).isTrue() // first delivery wins
        assertThat(repository.enqueue(pending(sourceEventId))).isFalse() // re-delivery is deduped

        assertThat(repository.recent(10)).hasSize(1)
    }

    @Test
    fun `findPending returns unsent oldest-first and markSent removes from pending`() {
        val older = pending(UUID.randomUUID(), createdAt = Instant.parse("2026-05-01T08:00:00Z"))
        val newer = pending(UUID.randomUUID(), createdAt = Instant.parse("2026-05-01T09:00:00Z"))
        repository.enqueue(newer)
        repository.enqueue(older)

        assertThat(repository.findPending(10).map { it.id }).isEqualTo(listOf(older.id, newer.id))

        repository.markSent(older.id, Instant.parse("2026-05-01T10:00:00Z"))

        assertThat(repository.findPending(10).map { it.id }).isEqualTo(listOf(newer.id))
    }

    @Test
    fun `recent returns newest-first`() {
        val older = pending(UUID.randomUUID(), createdAt = Instant.parse("2026-05-01T08:00:00Z"))
        val newer = pending(UUID.randomUUID(), createdAt = Instant.parse("2026-05-01T09:00:00Z"))
        repository.enqueue(older)
        repository.enqueue(newer)

        assertThat(repository.recent(10).map { it.id }).isEqualTo(listOf(newer.id, older.id))
    }
}
