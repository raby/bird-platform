package com.digitalbluebird.integrations.ebird.adapter.inbound.schedule

import com.digitalbluebird.integrations.ebird.domain.port.inbound.DispatchEbirdSubmissionsUseCase
import org.springframework.scheduling.annotation.Scheduled

/**
 * Production trigger for the eBird sync: drains pending submissions on a fixed delay (default 2s,
 * overridable via `bird.ebird.dispatch-delay-ms`). A thin scheduling shim — the resilience and
 * dispatch logic live on the dispatcher. Requires `@EnableScheduling` on the app (already present).
 */
class EbirdDispatchPoller(
    private val dispatcher: DispatchEbirdSubmissionsUseCase,
) {
    @Scheduled(fixedDelayString = "\${bird.ebird.dispatch-delay-ms:2000}")
    fun poll() {
        dispatcher.dispatchPending(DEFAULT_BATCH_SIZE)
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 50
    }
}
