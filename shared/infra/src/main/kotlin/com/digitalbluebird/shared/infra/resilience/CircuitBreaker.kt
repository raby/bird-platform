package com.digitalbluebird.shared.infra.resilience

import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Thrown when a call is short-circuited because the breaker is OPEN (or a HALF_OPEN trial is busy). */
class CircuitOpenException(message: String) : RuntimeException(message)

/**
 * A minimal circuit breaker for guarding an unreliable dependency.
 *
 * CLOSED → (after [failureThreshold] consecutive failures) → OPEN → (after [resetTimeout]) → HALF_OPEN
 * → (one trial call) → CLOSED on success, back to OPEN on failure. While OPEN the breaker fails fast
 * with [CircuitOpenException] instead of calling the dependency, so a service that is down is not
 * hammered; HALF_OPEN lets exactly one caller probe for recovery.
 *
 * Thread-safe: all state transitions are guarded by a lock, and only one HALF_OPEN trial runs at a
 * time, so concurrent callers see a consistent state. Time comes from an injected [Clock] so the reset
 * window is testable without sleeping.
 */
class CircuitBreaker(
    private val failureThreshold: Int,
    private val resetTimeout: Duration,
    private val clock: Clock,
    private val name: String = "circuit",
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    init {
        require(failureThreshold >= 1) { "failureThreshold must be at least 1" }
        require(!resetTimeout.isNegative) { "resetTimeout must not be negative" }
    }

    private val lock = Any()
    private var state = State.CLOSED
    private var consecutiveFailures = 0
    private var openedAt: Instant? = null
    private var halfOpenInFlight = false

    /** The breaker's state right now, treating an elapsed OPEN window as HALF_OPEN. A pure read. */
    val currentState: State get() = synchronized(lock) { effectiveState() }

    /**
     * Run [block] under the breaker. Throws [CircuitOpenException] without calling it when the breaker
     * is OPEN (or when a HALF_OPEN trial is already in flight). Any throwable from [block] is counted
     * as a failure (and the HALF_OPEN trial slot released) and re-thrown.
     */
    fun <T> execute(block: () -> T): T {
        synchronized(lock) {
            when (effectiveState()) {
                State.OPEN -> throw CircuitOpenException("$name is open")
                State.HALF_OPEN -> {
                    if (halfOpenInFlight) throw CircuitOpenException("$name is half-open; a trial is in flight")
                    state = State.HALF_OPEN // persist the transition now that we are claiming the trial
                    halfOpenInFlight = true
                }
                State.CLOSED -> Unit
            }
        }
        var succeeded = false
        try {
            val result = block()
            succeeded = true
            return result
        } finally {
            // In a finally so an Error (not just an Exception) still releases the trial slot rather
            // than wedging the breaker half-open forever.
            if (succeeded) onSuccess() else onFailure()
        }
    }

    // The state an elapsed reset window implies, without mutating anything. Caller must hold the lock.
    private fun effectiveState(): State =
        if (state == State.OPEN && openedAt?.let { !clock.instant().isBefore(it.plus(resetTimeout)) } == true) {
            State.HALF_OPEN
        } else {
            state
        }

    private fun onSuccess() = synchronized(lock) {
        consecutiveFailures = 0
        halfOpenInFlight = false
        state = State.CLOSED
        openedAt = null
    }

    private fun onFailure() = synchronized(lock) {
        consecutiveFailures++
        halfOpenInFlight = false
        // A failed HALF_OPEN trial reopens immediately; in CLOSED, open once the threshold is reached.
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN
            openedAt = clock.instant()
        }
    }
}
