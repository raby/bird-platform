package com.digitalbluebird.observations.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.digitalbluebird.observations.domain.Count
import com.digitalbluebird.observations.domain.ObservationError
import com.digitalbluebird.observations.domain.Sighting
import com.digitalbluebird.observations.domain.SightingId
import com.digitalbluebird.observations.domain.event.SightingCreatedEvent
import com.digitalbluebird.observations.domain.port.inbound.FindSightingUseCase
import com.digitalbluebird.observations.domain.port.inbound.RecordSightingCommand
import com.digitalbluebird.observations.domain.port.inbound.RecordSightingUseCase
import com.digitalbluebird.observations.domain.port.outbound.SightingRepository
import com.digitalbluebird.shared.domain.Location
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.shared.domain.SpeciesId
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.digitalbluebird.shared.infra.outbox.OutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

open class SightingService(
    private val sightings: SightingRepository,
    private val outbox: OutboxRepository,
    private val clock: Clock,
    private val objectMapper: ObjectMapper,
) : RecordSightingUseCase, FindSightingUseCase {

    @Transactional
    override fun record(command: RecordSightingCommand): Either<ObservationError, Sighting> {
        val observerId = try {
            ObserverId(UUID.fromString(command.observerId))
        } catch (e: IllegalArgumentException) {
            return ObservationError.InvalidObserverId("observerId must be a UUID").left()
        }

        val speciesId = try {
            SpeciesId(command.speciesId)
        } catch (e: IllegalArgumentException) {
            return ObservationError.InvalidSpeciesId(e.message ?: "invalid speciesId").left()
        }

        val location = try {
            Location.of(command.latitude, command.longitude)
        } catch (e: IllegalArgumentException) {
            return ObservationError.InvalidLocation(e.message ?: "invalid location").left()
        }

        val count = try {
            Count(command.count)
        } catch (e: IllegalArgumentException) {
            return ObservationError.InvalidCount(e.message ?: "invalid count").left()
        }

        val now = clock.instant()
        if (command.observedAt.isAfter(now)) {
            return ObservationError.ObservedAtInFuture(command.observedAt, now).left()
        }

        val sighting = Sighting(
            id = SightingId.random(),
            observerId = observerId,
            speciesId = speciesId,
            location = location,
            observedAt = command.observedAt,
            count = count,
            notes = command.notes?.trim()?.takeIf { it.isNotBlank() },
            createdAt = now,
        )

        sightings.save(sighting)

        val event = SightingCreatedEvent(
            sightingId = sighting.id.value.toString(),
            observerId = sighting.observerId.value.toString(),
            speciesId = sighting.speciesId.value,
            latitude = sighting.location.latitude.value,
            longitude = sighting.location.longitude.value,
            observedAt = sighting.observedAt,
            count = sighting.count.value,
            occurredAt = now,
        )
        outbox.save(
            OutboxEntry(
                id = UUID.randomUUID(),
                aggregateType = SightingCreatedEvent.AGGREGATE_TYPE,
                aggregateId = sighting.id.value.toString(),
                eventType = SightingCreatedEvent.EVENT_TYPE,
                payload = objectMapper.writeValueAsString(event),
                occurredAt = now,
            ),
        )

        return sighting.right()
    }

    @Transactional(readOnly = true)
    override fun findById(id: SightingId): Either<ObservationError, Sighting> =
        sightings.findById(id)?.right() ?: ObservationError.SightingNotFound(id).left()
}
