package com.digitalbluebird.reserves.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.digitalbluebird.reserves.domain.NearbyReserve
import com.digitalbluebird.reserves.domain.Reserve
import com.digitalbluebird.reserves.domain.ReserveError
import com.digitalbluebird.reserves.domain.ReserveId
import com.digitalbluebird.reserves.domain.port.inbound.FindReservesUseCase
import com.digitalbluebird.reserves.domain.port.outbound.ReserveRepository

/**
 * Reads the reserve catalogue. The distinctive part is [nearby] — a geospatial search answered by the
 * repository with PostGIS. The service validates the query (a point on Earth, a positive radius) and
 * leaves the spatial work to the adapter.
 */
class ReserveService(
    private val reserves: ReserveRepository,
) : FindReservesUseCase {

    override fun nearby(latitude: Double, longitude: Double, radiusKm: Double): Either<ReserveError, List<NearbyReserve>> {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return ReserveError.InvalidCoordinates("latitude must be in -90..90 and longitude in -180..180").left()
        }
        if (radiusKm <= 0.0) return ReserveError.InvalidRadius("radiusKm must be greater than 0").left()
        return reserves.findNearby(latitude, longitude, radiusKm).right()
    }

    override fun all(): List<Reserve> = reserves.findAll()

    override fun byId(id: String): Either<ReserveError, Reserve> {
        val reserveId = runCatching { ReserveId.fromString(id) }.getOrNull()
            ?: return ReserveError.InvalidReserveId("not a valid reserve id: $id").left()
        return reserves.findById(reserveId)?.right() ?: ReserveError.ReserveNotFound(reserveId).left()
    }
}
