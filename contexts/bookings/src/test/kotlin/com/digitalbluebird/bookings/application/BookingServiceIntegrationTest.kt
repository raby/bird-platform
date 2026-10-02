package com.digitalbluebird.bookings.application

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.digitalbluebird.bookings.adapter.outbound.persistence.JdbcBookingRepository
import com.digitalbluebird.bookings.adapter.outbound.persistence.JdbcIdempotencyKeysRepository
import com.digitalbluebird.bookings.adapter.outbound.persistence.OverlappingSlotException
import com.digitalbluebird.bookings.domain.BookingError
import com.digitalbluebird.bookings.domain.BookingStatus
import com.digitalbluebird.bookings.domain.event.BookingConfirmedEvent
import com.digitalbluebird.bookings.domain.event.BookingRequestedEvent
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingCommand
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingServiceIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private val fixedInstant: Instant = Instant.parse("2026-05-01T09:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private lateinit var service: BookingService
    private lateinit var jdbc: JdbcTemplate
    private lateinit var tx: TransactionTemplate

    private val hideId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val observerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

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
        // Each service call runs in its own transaction, mirroring the @Transactional boundary the
        // Spring proxy applies in production. request() relies on it: the idempotency key and the
        // booking it references are inserted in one transaction, and the deferred FK is validated at
        // commit. It also makes the confirm-race below two genuinely concurrent transactions.
        tx = TransactionTemplate(DataSourceTransactionManager(dataSource))
        service = BookingService(
            bookings = JdbcBookingRepository(named),
            idempotencyKeys = JdbcIdempotencyKeysRepository(named),
            outbox = JdbcOutboxRepository(named),
            clock = clock,
            objectMapper = objectMapper,
        )
    }

    /** Run a service call in a transaction, as @Transactional would in the running app. */
    private fun <T> inTx(block: () -> T): T = tx.execute { block() }!!

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE bookings.idempotency_keys, bookings.bookings")
        jdbc.execute("TRUNCATE TABLE shared_infra.outbox")
    }

    private fun command(
        key: String = "req-${UUID.randomUUID().toString().take(8)}",
        startOffsetSec: Long = 3600,
        endOffsetSec: Long = 7200,
    ) = RequestBookingCommand(
        idempotencyKey = key,
        hideId = hideId.toString(),
        observerId = observerId.toString(),
        slotStart = fixedInstant.plusSeconds(startOffsetSec),
        slotEnd = fixedInstant.plusSeconds(endOffsetSec),
        partySize = 2,
    )

    @Test
    fun `request persists booking, idempotency key, and outbox entry atomically`() {
        val result = inTx { service.request(command(key = "idem-firstreq-01")) }

        assertThat(result.isRight()).isTrue()
        val booking = result.getOrNull()!!

        val bookingCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings.bookings WHERE id = ?",
            Long::class.java,
            booking.id.value,
        )
        assertThat(bookingCount).isEqualTo(1L)

        val mappedId = jdbc.queryForObject(
            "SELECT booking_id FROM bookings.idempotency_keys WHERE key = ?",
            UUID::class.java,
            "idem-firstreq-01",
        )
        assertThat(mappedId).isEqualTo(booking.id.value)

        val outbox = jdbc.queryForList(
            "SELECT aggregate_type, aggregate_id, event_type, payload::text AS payload FROM shared_infra.outbox",
        )
        assertThat(outbox).hasSize(1)
        val event = objectMapper.readValue(outbox.single()["payload"] as String, BookingRequestedEvent::class.java)
        assertThat(event.bookingId).isEqualTo(booking.id.value.toString())
        assertThat(event.partySize).isEqualTo(2)
    }

    @Test
    fun `re-using an idempotency key returns the same booking and writes no second row`() {
        val key = "idem-deduplicated-7"
        val first = inTx { service.request(command(key = key)) }.getOrNull()!!
        val second = inTx { service.request(command(key = key)) }.getOrNull()!!

        assertThat(second.id).isEqualTo(first.id)

        val rows = jdbc.queryForObject("SELECT COUNT(*) FROM bookings.bookings", Long::class.java)
        assertThat(rows).isEqualTo(1L)
        val outbox = jdbc.queryForObject("SELECT COUNT(*) FROM shared_infra.outbox", Long::class.java)
        assertThat(outbox).isEqualTo(1L)
    }

    @Test
    fun `overlapping slot on same hide is rejected`() {
        inTx { service.request(command(key = "idem-first-slot-1", startOffsetSec = 3600, endOffsetSec = 7200)) }.getOrNull()!!
        val second = inTx { service.request(command(key = "idem-overlap-slot", startOffsetSec = 5400, endOffsetSec = 9000)) }

        assertThat(second.leftOrNull()!!).isInstanceOf(BookingError.HideSlotUnavailable::class)
    }

    @Test
    fun `confirm increments version and writes BookingConfirmed event`() {
        val requested = inTx { service.request(command(key = "idem-confirm-flow")) }.getOrNull()!!
        val confirmed = inTx { service.confirm(requested.id, expectedVersion = 0L) }.getOrNull()!!

        assertThat(confirmed.status).isEqualTo(BookingStatus.CONFIRMED)
        assertThat(confirmed.version).isEqualTo(1L)

        val eventTypes = jdbc.queryForList(
            "SELECT event_type FROM shared_infra.outbox ORDER BY occurred_at",
            String::class.java,
        )
        assertThat(eventTypes).containsExactly("BookingRequested", "BookingConfirmed")

        val confirmPayload = jdbc.queryForObject(
            "SELECT payload::text FROM shared_infra.outbox WHERE event_type = 'BookingConfirmed'",
            String::class.java,
        )!!
        val event = objectMapper.readValue(confirmPayload, BookingConfirmedEvent::class.java)
        assertThat(event.bookingId).isEqualTo(requested.id.value.toString())
    }

    @Test
    fun `two concurrent confirms race and exactly one wins via optimistic locking`() {
        val booking = inTx { service.request(command(key = "idem-race-setup")) }.getOrNull()!!

        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val winners = java.util.concurrent.atomic.AtomicInteger()
        val losers = java.util.concurrent.atomic.AtomicInteger()

        repeat(2) {
            executor.submit {
                try {
                    start.await()
                    val result = inTx { service.confirm(booking.id, expectedVersion = 0L) }
                    result.fold(
                        ifLeft = { err ->
                            if (err is BookingError.VersionConflict) losers.incrementAndGet()
                        },
                        ifRight = { winners.incrementAndGet() },
                    )
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue()
        executor.shutdown()

        assertThat(winners.get()).isEqualTo(1)
        assertThat(losers.get()).isEqualTo(1)

        val finalVersion = jdbc.queryForObject(
            "SELECT version FROM bookings.bookings WHERE id = ?",
            Long::class.java,
            booking.id.value,
        )
        assertThat(finalVersion).isEqualTo(1L)

        val confirmedEvents = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shared_infra.outbox WHERE event_type = 'BookingConfirmed'",
            Long::class.java,
        )
        assertThat(confirmedEvents).isEqualTo(1L)
    }

    @Test
    fun `cancel after confirm transitions through the state machine`() {
        val requested = inTx { service.request(command(key = "idem-cancel-after")) }.getOrNull()!!
        val confirmed = inTx { service.confirm(requested.id, expectedVersion = 0L) }.getOrNull()!!
        val cancelled = inTx { service.cancel(confirmed.id, expectedVersion = 1L) }.getOrNull()!!

        assertThat(cancelled.status).isEqualTo(BookingStatus.CANCELLED)
        assertThat(cancelled.version).isEqualTo(2L)

        val eventTypes = jdbc.queryForList(
            "SELECT event_type FROM shared_infra.outbox ORDER BY occurred_at",
            String::class.java,
        )
        assertThat(eventTypes).containsExactly("BookingRequested", "BookingConfirmed", "BookingCancelled")
    }

    @Test
    fun `cancel cannot transition from already-cancelled state`() {
        val requested = inTx { service.request(command(key = "idem-double-cancel")) }.getOrNull()!!
        inTx { service.cancel(requested.id, expectedVersion = 0L) }.getOrNull()!!

        val second = inTx { service.cancel(requested.id, expectedVersion = 1L) }
        assertThat(second.leftOrNull()!!).isInstanceOf(BookingError.StateTransitionNotAllowed::class)
    }

    @Test
    fun `two concurrent requests for an overlapping slot yield exactly one booking`() {
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val successes = java.util.concurrent.atomic.AtomicInteger()
        val rejections = java.util.concurrent.atomic.AtomicInteger()

        repeat(2) { i ->
            executor.submit {
                try {
                    start.await()
                    // Same hide, same (overlapping) slot, distinct idempotency keys — so slot
                    // exclusivity, not idempotency, decides the winner. The loser is rejected either
                    // by the pre-check (Left HideSlotUnavailable) or, if both passed it before either
                    // committed, by the V303 exclusion constraint (OverlappingSlotException); the test
                    // accepts either, since both are the same 409 to a client.
                    runCatching {
                        inTx { service.request(command(key = "idem-slotrace-$i", startOffsetSec = 3600, endOffsetSec = 7200)) }
                    }.fold(
                        onSuccess = { either ->
                            either.fold(
                                ifLeft = { if (it is BookingError.HideSlotUnavailable) rejections.incrementAndGet() },
                                ifRight = { successes.incrementAndGet() },
                            )
                        },
                        onFailure = { if (it is OverlappingSlotException) rejections.incrementAndGet() },
                    )
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue()
        executor.shutdown()

        assertThat(successes.get()).isEqualTo(1)
        assertThat(rejections.get()).isEqualTo(1)

        // The storage-layer guarantee: exactly one active booking exists, whichever guard fired.
        val activeCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings.bookings WHERE status <> 'CANCELLED'",
            Long::class.java,
        )
        assertThat(activeCount).isEqualTo(1L)
    }
}
