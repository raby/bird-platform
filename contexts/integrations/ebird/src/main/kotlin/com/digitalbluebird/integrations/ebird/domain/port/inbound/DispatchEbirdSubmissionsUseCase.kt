package com.digitalbluebird.integrations.ebird.domain.port.inbound

/**
 * Attempt the pending eBird submissions, up to [limit], through the resilient gateway, and return how
 * many eBird accepted on this run. Transient failures are retried; a sustained outage trips the
 * circuit breaker so the run fails fast and defers the rest rather than hammering a dead service.
 */
fun interface DispatchEbirdSubmissionsUseCase {
    fun dispatchPending(limit: Int): Int
}
