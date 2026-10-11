package com.digitalbluebird.integrations.ebird.domain

import java.time.Instant

/**
 * One sighting's journey to eBird: enqueued PENDING when the sighting is recorded, moved to SUBMITTED
 * once the gateway accepts it, or FAILED after too many real failures (a dead letter). [sightingId] is
 * the idempotency key, so a re-delivered SightingCreated never queues a second submission.
 *
 * The sighting's data is captured here so the dispatcher is self-contained — it never calls back into
 * the observations context to submit.
 */
data class EbirdSubmission(
    val sightingId: String,
    val speciesId: String,
    val latitude: Double,
    val longitude: Double,
    val observedAt: Instant,
    val count: Int,
    val status: SubmissionStatus,
    val attempts: Int,
    val lastError: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val submittedAt: Instant? = null,
) {
    fun submitted(at: Instant): EbirdSubmission =
        copy(status = SubmissionStatus.SUBMITTED, submittedAt = at, updatedAt = at, lastError = null)

    /**
     * Record a real failed attempt. The submission stays PENDING for another try until [maxAttempts]
     * is reached, then becomes FAILED (a dead letter). A circuit-open fast-fail is NOT a real attempt
     * and must not call this — the dependency being down is not this submission's fault.
     */
    fun failedAttempt(error: String, at: Instant, maxAttempts: Int): EbirdSubmission {
        val tries = attempts + 1
        val next = if (tries >= maxAttempts) SubmissionStatus.FAILED else SubmissionStatus.PENDING
        return copy(status = next, attempts = tries, lastError = error, updatedAt = at)
    }

    companion object {
        fun pending(
            sightingId: String,
            speciesId: String,
            latitude: Double,
            longitude: Double,
            observedAt: Instant,
            count: Int,
            at: Instant,
        ): EbirdSubmission = EbirdSubmission(
            sightingId = sightingId,
            speciesId = speciesId,
            latitude = latitude,
            longitude = longitude,
            observedAt = observedAt,
            count = count,
            status = SubmissionStatus.PENDING,
            attempts = 0,
            lastError = null,
            createdAt = at,
            updatedAt = at,
        )
    }
}

enum class SubmissionStatus { PENDING, SUBMITTED, FAILED }
