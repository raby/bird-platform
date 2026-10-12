package com.digitalbluebird.reserves.domain

import java.util.UUID

@JvmInline
value class ReserveId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun fromString(s: String): ReserveId = ReserveId(UUID.fromString(s))
    }
}

/** A nature reserve at a point on the map. */
data class Reserve(
    val id: ReserveId,
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
)

/** A reserve together with how far it lies from a searched-for point. */
data class NearbyReserve(
    val reserve: Reserve,
    val distanceKm: Double,
)
