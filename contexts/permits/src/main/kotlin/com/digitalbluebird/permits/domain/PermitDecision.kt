package com.digitalbluebird.permits.domain

/**
 * The outcome of trying to issue a visitor permit for a booking against a hide's daily cap: either it
 * fit (or no cap is configured for that hide and day), or the day's cap is already exhausted. The
 * permits saga acts on [Exhausted] by compensating — cancelling the over-cap booking.
 */
sealed interface PermitDecision {
    data object Issued : PermitDecision
    data object Exhausted : PermitDecision
}
