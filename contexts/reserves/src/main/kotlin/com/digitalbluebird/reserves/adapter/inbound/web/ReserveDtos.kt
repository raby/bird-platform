package com.digitalbluebird.reserves.adapter.inbound.web

import com.digitalbluebird.reserves.domain.NearbyReserve
import com.digitalbluebird.reserves.domain.Reserve

data class ReserveResponse(
    val id: String,
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        fun from(r: Reserve) = ReserveResponse(r.id.toString(), r.name, r.region, r.latitude, r.longitude)
    }
}

data class NearbyReserveResponse(
    val reserve: ReserveResponse,
    val distanceKm: Double,
) {
    companion object {
        fun from(n: NearbyReserve) = NearbyReserveResponse(ReserveResponse.from(n.reserve), n.distanceKm)
    }
}

data class ErrorResponse(val code: String, val message: String)
