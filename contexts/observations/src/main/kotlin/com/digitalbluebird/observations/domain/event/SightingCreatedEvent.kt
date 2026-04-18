package com.digitalbluebird.observations.domain.event

import java.time.Instant

data class SightingCreatedEvent(
    val sightingId: String,
    val observerId: String,
    val speciesId: String,
    val latitude: Double,
    val longitude: Double,
    val observedAt: Instant,
    val count: Int,
    val occurredAt: Instant,
) {
    companion object {
        const val EVENT_TYPE = "SightingCreated"
        const val AGGREGATE_TYPE = "Sighting"
    }
}
