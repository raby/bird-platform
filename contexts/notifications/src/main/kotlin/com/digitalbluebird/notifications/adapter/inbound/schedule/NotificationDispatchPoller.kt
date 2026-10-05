package com.digitalbluebird.notifications.adapter.inbound.schedule

import com.digitalbluebird.notifications.domain.port.inbound.DispatchNotificationsUseCase
import org.springframework.scheduling.annotation.Scheduled

/**
 * Production trigger for notification delivery: drains pending notifications on a fixed delay (default
 * 1s, overridable via `bird.notifications.dispatch-delay-ms`). A thin scheduling shim, like the outbox
 * poller — the delivery logic and its tests live on the dispatcher. Requires `@EnableScheduling` on
 * the app (already present for the outbox poller).
 */
class NotificationDispatchPoller(
    private val dispatcher: DispatchNotificationsUseCase,
) {
    @Scheduled(fixedDelayString = "\${bird.notifications.dispatch-delay-ms:1000}")
    fun poll() {
        dispatcher.dispatchPending(DEFAULT_BATCH_SIZE)
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 100
    }
}
