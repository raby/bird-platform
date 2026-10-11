package com.digitalbluebird.integrations.ebird.domain.port.outbound

import java.time.Instant

/**
 * The outbound port to eBird. Implementations translate [SightingToSync] (our terms) into eBird's own
 * submission format — the anti-corruption layer — and call their API, throwing on any failure so the
 * caller's resilience (retry, circuit breaker) can react. Keeping the port in our vocabulary is what
 * stops eBird's field names and formats from leaking into the rest of the platform.
 */
fun interface EbirdGateway {
    fun submit(sighting: SightingToSync)
}

data class SightingToSync(
    val sightingId: String,
    val speciesId: String,
    val latitude: Double,
    val longitude: Double,
    val observedAt: Instant,
    val count: Int,
)
