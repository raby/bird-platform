package com.digitalbluebird.bookings.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.digitalbluebird.bookings.domain.Booking
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.BookingStatus
import com.digitalbluebird.bookings.domain.HideId
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
class JdbcBookingRepositoryIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private lateinit var repository: JdbcBookingRepository
    private lateinit var jdbc: JdbcTemplate

    private val hideA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val hideB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val observer = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val baseTime: Instant = Instant.parse("2026-05-01T08:00:00Z")

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
        repository = JdbcBookingRepository(NamedParameterJdbcTemplate(dataSource))
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE bookings.idempotency_keys, bookings.bookings")
    }

    private fun newBooking(
        hide: UUID = hideA,
        startOffsetSec: Long = 3600,
        endOffsetSec: Long = 7200,
    ): Booking = Booking.request(
        id = BookingId.random(),
        hideId = HideId(hide),
        observerId = ObserverId(observer),
        slot = InstantRange(baseTime.plusSeconds(startOffsetSec), baseTime.plusSeconds(endOffsetSec)),
        partySize = PartySize(3),
        now = baseTime,
    )

    @Test
    fun `insert persists booking and findById round-trips all fields`() {
        val booking = newBooking()
        repository.insert(booking)

        val loaded = repository.findById(booking.id)

        assertThat(loaded).isNotNull()
        assertThat(loaded).isEqualTo(booking)
        assertThat(loaded!!.version).isEqualTo(0L)
    }

    @Test
    fun `findById returns null when absent`() {
        assertThat(repository.findById(BookingId.random())).isNull()
    }

    @Test
    fun `updateIfVersionMatches succeeds when stored version matches and increments it`() {
        val booking = newBooking()
        repository.insert(booking)

        val confirmed = booking.copy(status = BookingStatus.CONFIRMED, updatedAt = baseTime.plusSeconds(60))
        val updated = repository.updateIfVersionMatches(confirmed, expectedVersion = 0L)

        assertThat(updated).isNotNull()
        assertThat(updated!!.version).isEqualTo(1L)

        val loaded = repository.findById(booking.id)!!
        assertThat(loaded.status).isEqualTo(BookingStatus.CONFIRMED)
        assertThat(loaded.version).isEqualTo(1L)
    }

    @Test
    fun `updateIfVersionMatches returns null when another writer already bumped the version`() {
        val booking = newBooking()
        repository.insert(booking)

        val firstWriter = booking.copy(status = BookingStatus.CONFIRMED, updatedAt = baseTime.plusSeconds(60))
        val afterFirst = repository.updateIfVersionMatches(firstWriter, expectedVersion = 0L)
        assertThat(afterFirst).isNotNull()

        val secondWriter = booking.copy(status = BookingStatus.CANCELLED, updatedAt = baseTime.plusSeconds(90))
        val afterSecond = repository.updateIfVersionMatches(secondWriter, expectedVersion = 0L)

        assertThat(afterSecond).isNull()

        val loaded = repository.findById(booking.id)!!
        assertThat(loaded.status).isEqualTo(BookingStatus.CONFIRMED)
        assertThat(loaded.version).isEqualTo(1L)
    }

    @Test
    fun `hasOverlappingActiveBooking detects overlap on the same hide`() {
        val existing = newBooking(startOffsetSec = 3600, endOffsetSec = 7200)
        repository.insert(existing)

        val overlap = InstantRange(baseTime.plusSeconds(5400), baseTime.plusSeconds(10800))
        assertThat(repository.hasOverlappingActiveBooking(HideId(hideA), overlap)).isTrue()
    }

    @Test
    fun `hasOverlappingActiveBooking is false for adjacent slot`() {
        val existing = newBooking(startOffsetSec = 3600, endOffsetSec = 7200)
        repository.insert(existing)

        val adjacent = InstantRange(baseTime.plusSeconds(7200), baseTime.plusSeconds(10800))
        assertThat(repository.hasOverlappingActiveBooking(HideId(hideA), adjacent)).isFalse()
    }

    @Test
    fun `hasOverlappingActiveBooking is false for a different hide`() {
        val existing = newBooking(hide = hideA)
        repository.insert(existing)

        val overlap = InstantRange(baseTime.plusSeconds(5400), baseTime.plusSeconds(10800))
        assertThat(repository.hasOverlappingActiveBooking(HideId(hideB), overlap)).isFalse()
    }

    @Test
    fun `hasOverlappingActiveBooking ignores cancelled bookings`() {
        val existing = newBooking()
        repository.insert(existing)
        val cancelled = existing.copy(status = BookingStatus.CANCELLED, updatedAt = baseTime.plusSeconds(5))
        repository.updateIfVersionMatches(cancelled, expectedVersion = 0L)

        val sameSlot = existing.slot
        assertThat(repository.hasOverlappingActiveBooking(HideId(hideA), sameSlot)).isFalse()
    }

    // ── V303: the slot-exclusivity constraint, the storage-layer backstop behind the pre-check ──

    @Test
    fun `insert is rejected by the exclusion constraint for an overlapping active booking`() {
        repository.insert(newBooking(startOffsetSec = 3600, endOffsetSec = 7200))
        // Same hide, overlapping slot, both active: the constraint must reject the second insert,
        // surfaced as OverlappingSlotException (regardless of the service-layer pre-check).
        assertThat(runCatching { repository.insert(newBooking(startOffsetSec = 5400, endOffsetSec = 10800)) })
            .isFailure().isInstanceOf(OverlappingSlotException::class)
    }

    @Test
    fun `insert allows a touching slot and a different hide`() {
        repository.insert(newBooking(startOffsetSec = 3600, endOffsetSec = 7200))
        // Adjacent half-open [) ranges touch at 7200 but do not overlap.
        assertThat(repository.insert(newBooking(startOffsetSec = 7200, endOffsetSec = 10800))).isNotNull()
        // A different hide is unaffected by the constraint.
        assertThat(repository.insert(newBooking(hide = hideB, startOffsetSec = 5400, endOffsetSec = 10800))).isNotNull()
    }

    @Test
    fun `insert reuses a slot freed by cancellation`() {
        val first = newBooking(startOffsetSec = 3600, endOffsetSec = 7200)
        repository.insert(first)
        repository.updateIfVersionMatches(
            first.copy(status = BookingStatus.CANCELLED, updatedAt = baseTime.plusSeconds(5)),
            expectedVersion = 0L,
        )
        // The cancelled booking drops out of the partial constraint, so the slot is bookable again.
        assertThat(repository.insert(newBooking(startOffsetSec = 3600, endOffsetSec = 7200))).isNotNull()
    }
}
