package com.digitalbluebird.integrations.ebird.adapter.inbound.web

import com.digitalbluebird.integrations.ebird.domain.EbirdSubmission
import java.time.Instant

data class EbirdSubmissionResponse(
    val sightingId: String,
    val speciesId: String,
    val status: String,
    val attempts: Int,
    val lastError: String?,
    val createdAt: Instant,
    val submittedAt: Instant?,
) {
    companion object {
        fun from(s: EbirdSubmission) = EbirdSubmissionResponse(
            sightingId = s.sightingId,
            speciesId = s.speciesId,
            status = s.status.name,
            attempts = s.attempts,
            lastError = s.lastError,
            createdAt = s.createdAt,
            submittedAt = s.submittedAt,
        )
    }
}

data class CircuitResponse(val state: String)
