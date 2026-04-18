package com.digitalbluebird.observations.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.digitalbluebird.observations.domain.ObservationError
import com.digitalbluebird.observations.domain.Sighting
import com.digitalbluebird.observations.domain.event.SightingCreatedEvent
import com.digitalbluebird.observations.domain.port.inbound.RecordSightingCommand
import com.digitalbluebird.observations.domain.port.outbound.SightingRepository
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.digitalbluebird.shared.infra.outbox.OutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SightingServiceTest {

    private val fixedInstant: Instant = Instant.parse("2026-04-18T12:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val sightings: SightingRepository = mockk(relaxed = true)
    private val outbox: OutboxRepository = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val service = SightingService(sightings, outbox, clock, objectMapper)

    private val validObserver = UUID.fromString("00000000-0000-0000-0000-000000000001").toString()

    private fun validCommand() = RecordSightingCommand(
        observerId = validObserver,
        speciesId = "turtur-turtur",
        latitude = 52.2,
        longitude = 0.1,
        observedAt = fixedInstant.minusSeconds(3600),
        count = 3,
        notes = "  pair foraging  ",
    )

    @Test
    fun `record persists sighting and writes outbox with serialised event payload`() {
        val savedSighting = slot<Sighting>()
        every { sightings.save(capture(savedSighting)) } answers { savedSighting.captured }
        val savedEntry = slot<OutboxEntry>()
        every { outbox.save(capture(savedEntry)) } answers { }

        val result = service.record(validCommand())

        assertThat(result.isRight()).isTrue()
        val sighting = result.getOrNull()!!
        assertThat(sighting.notes).isEqualTo("pair foraging")
        assertThat(sighting.createdAt).isEqualTo(fixedInstant)
        assertThat(sighting.count.value).isEqualTo(3)

        val entry = savedEntry.captured
        assertThat(entry.aggregateType).isEqualTo(SightingCreatedEvent.AGGREGATE_TYPE)
        assertThat(entry.aggregateId).isEqualTo(sighting.id.value.toString())
        assertThat(entry.eventType).isEqualTo(SightingCreatedEvent.EVENT_TYPE)
        assertThat(entry.occurredAt).isEqualTo(fixedInstant)

        val deserialised = objectMapper.readValue(entry.payload, SightingCreatedEvent::class.java)
        assertThat(deserialised.sightingId).isEqualTo(sighting.id.value.toString())
        assertThat(deserialised.observerId).isEqualTo(validObserver)
        assertThat(deserialised.speciesId).isEqualTo("turtur-turtur")
        assertThat(deserialised.latitude).isEqualTo(52.2)
        assertThat(deserialised.longitude).isEqualTo(0.1)
        assertThat(deserialised.count).isEqualTo(3)
        assertThat(deserialised.occurredAt).isEqualTo(fixedInstant)

        verify(exactly = 1) { sightings.save(any()) }
        verify(exactly = 1) { outbox.save(any()) }
    }

    @Test
    fun `record rejects non-UUID observerId`() {
        val result = service.record(validCommand().copy(observerId = "not-a-uuid"))
        assertThat(result.leftOrNull()!!).isInstanceOf(ObservationError.InvalidObserverId::class)
        verify(exactly = 0) { sightings.save(any()) }
        verify(exactly = 0) { outbox.save(any()) }
    }

    @Test
    fun `record rejects blank speciesId`() {
        val result = service.record(validCommand().copy(speciesId = "   "))
        assertThat(result.leftOrNull()!!).isInstanceOf(ObservationError.InvalidSpeciesId::class)
    }

    @Test
    fun `record rejects out-of-range latitude`() {
        val result = service.record(validCommand().copy(latitude = 91.0))
        assertThat(result.leftOrNull()!!).isInstanceOf(ObservationError.InvalidLocation::class)
    }

    @Test
    fun `record rejects non-positive count`() {
        val result = service.record(validCommand().copy(count = 0))
        assertThat(result.leftOrNull()!!).isInstanceOf(ObservationError.InvalidCount::class)
    }

    @Test
    fun `record rejects observedAt in the future`() {
        val result = service.record(validCommand().copy(observedAt = fixedInstant.plusSeconds(60)))
        assertThat(result.leftOrNull()!!).isInstanceOf(ObservationError.ObservedAtInFuture::class)
    }

    @Test
    fun `findById returns SightingNotFound when absent`() {
        every { sightings.findById(any()) } returns null
        val result = service.findById(com.digitalbluebird.observations.domain.SightingId.random())
        assertThat(result.leftOrNull()!!).isInstanceOf(ObservationError.SightingNotFound::class)
    }
}
