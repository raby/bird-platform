package com.digitalbluebird.integrations.ebird.adapter.outbound.ebird

import com.digitalbluebird.integrations.ebird.domain.port.outbound.EbirdGateway
import com.digitalbluebird.integrations.ebird.domain.port.outbound.SightingToSync
import org.slf4j.LoggerFactory
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Thrown when the (stubbed) eBird API is unreachable or rejects a submission. */
class EbirdUnavailableException(message: String) : RuntimeException(message)

/**
 * Stand-in for eBird's REST API. A real adapter would POST to their endpoint; this one logs. Either
 * way the job is the same, and it is the point of this adapter: the anti-corruption layer translating
 * our [SightingToSync] into eBird's own [EbirdChecklist] shape, so eBird's vocabulary never reaches the
 * domain.
 *
 * So the resilience is demonstrable offline, it throws for any sighting whose species id contains
 * "unreachable", standing in for eBird being down for that record — enough failures and the circuit
 * breaker that wraps this gateway will trip.
 */
class StubEbirdGateway : EbirdGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun submit(sighting: SightingToSync) {
        val checklist = translate(sighting)
        if (sighting.speciesId.contains("unreachable")) {
            throw EbirdUnavailableException("eBird could not be reached for ${checklist.speciesCode}")
        }
        log.info("submitted to eBird: {}", checklist)
    }

    // The anti-corruption layer: our model → eBird's wire format. The speciesCode mapping is a stand-in
    // for the real scientific-name → 6-letter eBird code lookup. Internal so it can be asserted directly.
    internal fun translate(s: SightingToSync): EbirdChecklist = EbirdChecklist(
        speciesCode = s.speciesId.replace("-", "").take(8).lowercase(),
        latitude = LAT_LNG.format(s.latitude),
        longitude = LAT_LNG.format(s.longitude),
        obsDt = OBS_DT.format(s.observedAt.atZone(ZoneOffset.UTC)),
        howMany = s.count,
        observationId = s.sightingId,
    )

    companion object {
        private const val LAT_LNG = "%.5f"
        private val OBS_DT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
