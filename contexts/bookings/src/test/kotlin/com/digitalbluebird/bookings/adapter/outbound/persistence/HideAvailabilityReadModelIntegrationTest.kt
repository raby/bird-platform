package com.digitalbluebird.bookings.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.digitalbluebird.bookings.application.BookingService
import com.digitalbluebird.bookings.application.HideAvailabilityProjector
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.PartySize
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingCommand
import com.digitalbluebird.bookings.domain.port.outbound.ConfirmedStay
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.shared.infra.outbox.JdbcOutboxRepository
import com.digitalbluebird.shared.infra.outbox.OutboxRelay
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
class HideAvailabilityReadModelIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private val fixedInstant: Instant = Instant.parse("2026-06-01T09:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private lateinit var jdbc: JdbcTemplate
    private lateinit var readModel: JdbcHideAvailabilityReadModel
    private lateinit var service: BookingService
    private lateinit var relay: OutboxRelay

    // Seeded by V301.
    private val kingfisher = HideId(UUID.fromString("10000000-0000-0000-0000-000000000001")) // capacity 6
    private val observer = ObserverId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))

    private fun slot(startOffsetSec: Long, endOffsetSec: Long) =
        InstantRange(fixedInstant.plusSeconds(startOffsetSec), fixedInstant.plusSeconds(endOffsetSec))

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker unavailable — skipping integration test")
        postgres.start()

        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
            setDriverClassName("org.postgresql.Driver")
        }
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/shared", "classpath:db/migration/bookings")
            .load()
            .migrate()

        jdbc = JdbcTemplate(dataSource)
        val named = NamedParameterJdbcTemplate(dataSource)
        readModel = JdbcHideAvailabilityReadModel(named)
        service = BookingService(
            bookings = JdbcBookingRepository(named),
            idempotencyKeys = JdbcIdempotencyKeysRepository(named),
            outbox = JdbcOutboxRepository(named),
            clock = clock,
            objectMapper = objectMapper,
        )
        relay = OutboxRelay(
            outbox = JdbcOutboxRepository(named),
            handlers = listOf(HideAvailabilityProjector(readModel, objectMapper)),
            clock = clock,
        )
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        // Keep the seeded bookings.hides; reset everything else.
        jdbc.execute("TRUNCATE TABLE bookings.hide_availability, bookings.idempotency_keys, bookings.bookings")
        jdbc.execute("TRUNCATE TABLE shared_infra.outbox")
    }

    private fun stay(bookingId: BookingId, slot: InstantRange, partySize: Int) = ConfirmedStay(
        bookingId = bookingId,
        hideId = kingfisher,
        observerId = observer,
        slot = slot,
        partySize = PartySize(partySize),
        confirmedAt = fixedInstant,
    )

    @Test
    fun `seeded hides are listed`() {
        val hides = readModel.listHides()
        assertThat(hides).hasSize(4)
        val kingfisherHide = hides.single { it.id == kingfisher }
        assertThat(kingfisherHide.name).isEqualTo("Kingfisher Hide")
        assertThat(kingfisherHide.capacity).isEqualTo(6)
    }

    @Test
    fun `availability of an unbooked hide is its full capacity`() {
        val availability = readModel.availabilityFor(kingfisher, slot(3600, 7200))
        assertThat(availability).isNotNull()
        assertThat(availability!!.occupied).isEqualTo(0)
        assertThat(availability.seatsLeft).isEqualTo(6)
    }

    @Test
    fun `applyConfirmed is reflected in occupancy and is idempotent`() {
        val bookingId = BookingId.random()
        readModel.applyConfirmed(stay(bookingId, slot(3600, 7200), partySize = 4))
        readModel.applyConfirmed(stay(bookingId, slot(3600, 7200), partySize = 4)) // re-delivery

        val availability = readModel.availabilityFor(kingfisher, slot(3600, 7200))!!
        assertThat(availability.occupied).isEqualTo(4) // counted once, not doubled
        assertThat(availability.seatsLeft).isEqualTo(2)
    }

    @Test
    fun `occupancy only counts bookings overlapping the queried slot`() {
        readModel.applyConfirmed(stay(BookingId.random(), slot(3600, 7200), partySize = 3))   // morning
        readModel.applyConfirmed(stay(BookingId.random(), slot(10800, 14400), partySize = 2)) // afternoon

        assertThat(readModel.availabilityFor(kingfisher, slot(3600, 7200))!!.occupied).isEqualTo(3)
        assertThat(readModel.availabilityFor(kingfisher, slot(10800, 14400))!!.occupied).isEqualTo(2)
        // a range spanning both sees the combined party sizes
        assertThat(readModel.availabilityFor(kingfisher, slot(0, 18000))!!.occupied).isEqualTo(5)
    }

    @Test
    fun `removeByBooking frees the occupancy`() {
        val bookingId = BookingId.random()
        readModel.applyConfirmed(stay(bookingId, slot(3600, 7200), partySize = 5))
        assertThat(readModel.availabilityFor(kingfisher, slot(3600, 7200))!!.occupied).isEqualTo(5)

        readModel.removeByBooking(bookingId)
        assertThat(readModel.availabilityFor(kingfisher, slot(3600, 7200))!!.occupied).isEqualTo(0)
    }

    @Test
    fun `availability of an unknown hide is null`() {
        val unknown = HideId(UUID.fromString("99999999-9999-9999-9999-999999999999"))
        assertThat(readModel.availabilityFor(unknown, slot(3600, 7200))).isNull()
    }

    @Test
    fun `end to end - confirm then drain the outbox projects occupancy, cancel then drain frees it`() {
        val command = RequestBookingCommand(
            idempotencyKey = "idem-e2e-project1",
            hideId = kingfisher.value.toString(),
            observerId = observer.value.toString(),
            slotStart = fixedInstant.plusSeconds(3600),
            slotEnd = fixedInstant.plusSeconds(7200),
            partySize = 3,
        )
        val requested = service.request(command).getOrNull()!!
        // Requested-but-unconfirmed contributes nothing, even after draining.
        relay.drainOnce(100)
        assertThat(readModel.availabilityFor(kingfisher, slot(3600, 7200))!!.occupied).isEqualTo(0)

        service.confirm(requested.id, expectedVersion = 0L).getOrNull()!!
        relay.drainOnce(100)
        assertThat(readModel.availabilityFor(kingfisher, slot(3600, 7200))!!.occupied).isEqualTo(3)

        service.cancel(requested.id, expectedVersion = 1L).getOrNull()!!
        relay.drainOnce(100)
        assertThat(readModel.availabilityFor(kingfisher, slot(3600, 7200))!!.occupied).isEqualTo(0)

        // Every outbox entry ends up published.
        val unpublished = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shared_infra.outbox WHERE published_at IS NULL",
            Long::class.java,
        )
        assertThat(unpublished).isEqualTo(0L)
    }
}
