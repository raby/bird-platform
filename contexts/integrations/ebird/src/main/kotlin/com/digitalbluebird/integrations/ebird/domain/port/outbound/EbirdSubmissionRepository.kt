package com.digitalbluebird.integrations.ebird.domain.port.outbound

import com.digitalbluebird.integrations.ebird.domain.EbirdSubmission

interface EbirdSubmissionRepository {
    /**
     * Persist a new PENDING submission, deduplicated on sightingId. Returns true if stored, false if a
     * submission for that sighting already existed — the idempotent half of the at-least-once consume.
     */
    fun enqueue(submission: EbirdSubmission): Boolean

    /** The oldest PENDING submissions, up to [limit], for dispatch. */
    fun findPending(limit: Int): List<EbirdSubmission>

    /** Persist a status / attempts / error change for an existing submission. */
    fun update(submission: EbirdSubmission)

    /** The most recent submissions, newest first, up to [limit]. */
    fun recent(limit: Int): List<EbirdSubmission>
}
