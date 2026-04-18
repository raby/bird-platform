package com.digitalbluebird.bookings.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.digitalbluebird.bookings.domain.Booking
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.IdempotencyKey
import com.digitalbluebird.bookings.domain.PartySize
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
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
class JdbcIdempotencyKeysRepositoryIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private lateinit var bookingRepo: JdbcBookingRepository
    private lateinit var repo: JdbcIdempotencyKeysRepository
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
                "classpath:db/migration/bookings",
            )
            .load()
            .migrate()

        jdbc = JdbcTemplate(dataSource)
        val named = NamedParameterJdbcTemplate(dataSource)
        bookingRepo = JdbcBookingRepository(named)
        repo = JdbcIdempotencyKeysRepository(named)
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE bookings.idempotency_keys, bookings.bookings")
    }

    private fun insertBooking(): Booking {
        val b = Booking.request(
            id = BookingId.random(),
            hideId = HideId(UUID.randomUUID()),
            observerId = ObserverId(UUID.randomUUID()),
            slot = InstantRange(Instant.parse("2026-05-01T10:00:00Z"), Instant.parse("2026-05-01T12:00:00Z")),
            partySize = PartySize(2),
            now = Instant.parse("2026-05-01T09:00:00Z"),
        )
        bookingRepo.insert(b)
        return b
    }

    @Test
    fun `register returns true on first use and false on second use of same key`() {
        val booking = insertBooking()
        val key = IdempotencyKey("first-call-abc123")

        assertThat(repo.register(key, booking.id)).isTrue()
        assertThat(repo.register(key, booking.id)).isFalse()
    }

    @Test
    fun `findBookingId returns the mapped booking id`() {
        val booking = insertBooking()
        val key = IdempotencyKey("lookup-xyz789")
        repo.register(key, booking.id)

        assertThat(repo.findBookingId(key)).isEqualTo(booking.id)
    }

    @Test
    fun `findBookingId returns null for unknown key`() {
        assertThat(repo.findBookingId(IdempotencyKey("unknown-key-1234"))).isNull()
    }

    @Test
    fun `register rejects a second booking id for the same key`() {
        val first = insertBooking()
        val second = insertBooking()
        val key = IdempotencyKey("race-winner-01")

        assertThat(repo.register(key, first.id)).isTrue()
        assertThat(repo.register(key, second.id)).isFalse()
        assertThat(repo.findBookingId(key)).isEqualTo(first.id)
    }
}
