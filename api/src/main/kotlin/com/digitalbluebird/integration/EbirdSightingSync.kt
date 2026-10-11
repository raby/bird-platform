package com.digitalbluebird.integration

import com.digitalbluebird.integrations.ebird.domain.port.inbound.SyncSightingCommand
import com.digitalbluebird.integrations.ebird.domain.port.inbound.SyncSightingUseCase
import com.digitalbluebird.observations.domain.event.SightingCreatedEvent
import com.digitalbluebird.shared.infra.outbox.OutboxEntry
import com.digitalbluebird.shared.infra.outbox.OutboxHandler
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Bridges a recorded sighting to the eBird integration. As an [OutboxHandler] it runs in the shared
 * relay's transaction, alongside the projector, saga and notifier, translating a SightingCreated event
 * into a generic [SyncSightingCommand] and queuing it on the ebird context. The ebird context imports
 * nothing from observations; this bridge, in the api composition root, is the only place that sees both.
 *
 * It queues (idempotently, keyed on the sighting id) rather than calling eBird here: the external call
 * is the dispatcher's job, kept off the outbox path so a slow or down eBird never stalls the relay.
 * Under the current transactional relay a committed entry is delivered once, so the dedup is
 * belt-and-suspenders; it is what keeps the queue correct the moment the same event can arrive more
 * than once — a non-transactional relay, or a real broker.
 */
class EbirdSightingSync(
    private val sync: SyncSightingUseCase,
    private val objectMapper: ObjectMapper,
) : OutboxHandler {

    override fun handle(entry: OutboxEntry) {
        if (entry.eventType != SightingCreatedEvent.EVENT_TYPE) return
        val event = objectMapper.readValue(entry.payload, SightingCreatedEvent::class.java)
        sync.sync(
            SyncSightingCommand(
                sightingId = event.sightingId,
                speciesId = event.speciesId,
                latitude = event.latitude,
                longitude = event.longitude,
                observedAt = event.observedAt,
                count = event.count,
            ),
        )
    }
}
