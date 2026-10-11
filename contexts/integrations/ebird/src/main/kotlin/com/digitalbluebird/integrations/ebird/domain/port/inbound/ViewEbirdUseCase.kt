package com.digitalbluebird.integrations.ebird.domain.port.inbound

import com.digitalbluebird.integrations.ebird.domain.EbirdSubmission

interface ViewEbirdUseCase {
    /** The most recent submissions, newest first, up to [limit]. */
    fun recent(limit: Int): List<EbirdSubmission>

    /** The eBird gateway circuit breaker's current state: CLOSED, OPEN or HALF_OPEN. */
    fun circuitState(): String
}
