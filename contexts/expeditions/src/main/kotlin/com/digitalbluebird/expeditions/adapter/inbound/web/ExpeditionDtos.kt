package com.digitalbluebird.expeditions.adapter.inbound.web

import com.digitalbluebird.expeditions.domain.Expedition
import java.time.Instant

data class PlanExpeditionRequest(
    val leaderId: String,
    val partySize: Int,
    val hides: List<HideRequestDto>,
)

data class HideRequestDto(
    val hideId: String,
    val slotStart: Instant,
    val slotEnd: Instant,
)

data class ExpeditionResponse(
    val id: String,
    val leaderId: String,
    val partySize: Int,
    val status: String,
    val legs: List<LegResponse>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(e: Expedition) = ExpeditionResponse(
            id = e.id.toString(),
            leaderId = e.leaderId,
            partySize = e.partySize,
            status = e.status.name,
            legs = e.legs.map { LegResponse(it.hideId, it.slotStart, it.slotEnd, it.bookingId, it.status.name) },
            createdAt = e.createdAt,
            updatedAt = e.updatedAt,
        )
    }
}

data class LegResponse(
    val hideId: String,
    val slotStart: Instant,
    val slotEnd: Instant,
    val bookingId: String?,
    val status: String,
)

data class ErrorResponse(val code: String, val message: String)
