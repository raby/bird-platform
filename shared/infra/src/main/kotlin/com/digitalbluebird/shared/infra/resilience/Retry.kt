package com.digitalbluebird.shared.infra.resilience

import java.time.Duration

/**
 * Run [block], retrying on exception up to [maxAttempts] total attempts with a fixed [delay] between
 * tries, and re-throw the last exception if they all fail. [shouldRetry] can exempt exceptions that
 * are pointless to retry (a validation error, say). [sleep] is injectable so tests need not wait.
 *
 * This handles the transient blip (a dropped connection, a brief 503); sustained failure is the
 * [CircuitBreaker]'s job, so the two compose as `breaker.execute { retry { call } }` — one logical,
 * already-retried call per breaker decision.
 */
fun <T> retry(
    maxAttempts: Int,
    delay: Duration = Duration.ZERO,
    shouldRetry: (Exception) -> Boolean = { true },
    sleep: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
    block: () -> T,
): T {
    require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            attempt++
            if (attempt >= maxAttempts || !shouldRetry(e)) throw e
            if (delay > Duration.ZERO) sleep(delay)
        }
    }
}
