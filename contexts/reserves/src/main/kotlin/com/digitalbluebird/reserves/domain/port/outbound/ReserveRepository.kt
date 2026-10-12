package com.digitalbluebird.reserves.domain.port.outbound

import com.digitalbluebird.reserves.domain.NearbyReserve
import com.digitalbluebird.reserves.domain.Reserve
import com.digitalbluebird.reserves.domain.ReserveId

interface ReserveRepository {
    /** Reserves within [radiusKm] of the point, nearest first (a spatial query). */
    fun findNearby(latitude: Double, longitude: Double, radiusKm: Double): List<NearbyReserve>

    fun findAll(): List<Reserve>

    fun findById(id: ReserveId): Reserve?
}
