package com.digitalbluebird.observations.adapter.inbound.web

import com.digitalbluebird.observations.domain.Sighting
import java.time.Instant

data class RecordSightingRequest(
    val observerId: String,
    val speciesId: String,
    val latitude: Double,
    val longitude: Double,
    val observedAt: Instant,
    val count: Int,
    val notes: String? = null,
)

data class SightingResponse(
    val id: String,
    val observerId: String,
    val speciesId: String,
    val latitude: Double,
    val longitude: Double,
    val observedAt: Instant,
    val count: Int,
    val notes: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(s: Sighting): SightingResponse = SightingResponse(
            id = s.id.value.toString(),
            observerId = s.observerId.value.toString(),
            speciesId = s.speciesId.value,
            latitude = s.location.latitude.value,
            longitude = s.location.longitude.value,
            observedAt = s.observedAt,
            count = s.count.value,
            notes = s.notes,
            createdAt = s.createdAt,
        )
    }
}

data class ErrorResponse(val code: String, val message: String)
