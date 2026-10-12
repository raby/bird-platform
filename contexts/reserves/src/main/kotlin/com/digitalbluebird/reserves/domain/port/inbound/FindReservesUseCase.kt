package com.digitalbluebird.reserves.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.reserves.domain.NearbyReserve
import com.digitalbluebird.reserves.domain.Reserve
import com.digitalbluebird.reserves.domain.ReserveError

interface FindReservesUseCase {
    /** Reserves within [radiusKm] of the point, nearest first. Left on invalid coordinates or radius. */
    fun nearby(latitude: Double, longitude: Double, radiusKm: Double): Either<ReserveError, List<NearbyReserve>>

    /** Every reserve, by name. */
    fun all(): List<Reserve>

    fun byId(id: String): Either<ReserveError, Reserve>
}
