package com.digitalbluebird.shared.infra.outbox

import org.springframework.scheduling.annotation.Scheduled

/**
 * Production trigger for the [OutboxRelay]: drains on a fixed delay (default 1s, overridable via
 * `bird.outbox.poll-delay-ms`). Kept a thin scheduling shim — tests drive the relay directly, so all
 * the delivery logic and its tests live on the relay. Requires `@EnableScheduling` on the app.
 */
class OutboxPoller(
    private val relay: OutboxRelay,
) {
    @Scheduled(fixedDelayString = "\${bird.outbox.poll-delay-ms:1000}")
    fun poll() {
        relay.drainOnce(OutboxRelay.DEFAULT_BATCH_SIZE)
    }
}
