package com.digitalbluebird.integrations.ebird.domain.port.inbound

import java.time.Instant

/**
 * Queue a recorded sighting for syncing to eBird. Idempotent on [SyncSightingCommand.sightingId]: the
 * at-least-once outbox may deliver SightingCreated more than once, and queuing the same sighting twice
 * must still yield a single submission.
 */
fun interface SyncSightingUseCase {
    fun sync(command: SyncSightingCommand)
}

data class SyncSightingCommand(
    val sightingId: String,
    val speciesId: String,
    val latitude: Double,
    val longitude: Double,
    val observedAt: Instant,
    val count: Int,
)
