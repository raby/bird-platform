package com.digitalbluebird.integration

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.integrations.ebird.domain.port.inbound.SyncSightingCommand
import com.digitalbluebird.integrations.ebird.domain.port.inbound.SyncSightingUseCase
import com.digitalbluebird.observations.domain.event.SightingCreatedEvent
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class EbirdSightingSyncTest {

    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val sync = mockk<SyncSightingUseCase>(relaxed = true)
    private val handler = EbirdSightingSync(sync, objectMapper)

    @Test
    fun `a SightingCreated event becomes a sync command`() {
        val event = SightingCreatedEvent(
            sightingId = UUID.randomUUID().toString(),
            observerId = UUID.randomUUID().toString(),
            speciesId = "turdus-merula",
            latitude = 54.1,
            longitude = -2.8,
            observedAt = Instant.parse("2026-05-02T08:00:00Z"),
            count = 2,
            occurredAt = Instant.parse("2026-05-02T08:05:00Z"),
        )
        val entry = OutboxEntry(
            id = UUID.randomUUID(),
            aggregateType = SightingCreatedEvent.AGGREGATE_TYPE,
            aggregateId = event.sightingId,
            eventType = SightingCreatedEvent.EVENT_TYPE,
            payload = objectMapper.writeValueAsString(event),
            occurredAt = event.occurredAt,
        )
        val command = slot<SyncSightingCommand>()
        every { sync.sync(capture(command)) } returns Unit

        handler.handle(entry)

        assertThat(command.captured.sightingId).isEqualTo(event.sightingId)
        assertThat(command.captured.speciesId).isEqualTo("turdus-merula")
        assertThat(command.captured.count).isEqualTo(2)
    }

    @Test
    fun `an unrelated event type is ignored`() {
        val entry = OutboxEntry(
            id = UUID.randomUUID(),
            aggregateType = "Booking",
            aggregateId = UUID.randomUUID().toString(),
            eventType = "BookingConfirmed",
            payload = "{}",
            occurredAt = Instant.parse("2026-05-02T08:00:00Z"),
        )

        handler.handle(entry)

        verify(exactly = 0) { sync.sync(any()) }
    }
}
