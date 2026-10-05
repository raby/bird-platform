package com.digitalbluebird.notifications.application

import com.digitalbluebird.notifications.domain.port.inbound.DispatchNotificationsUseCase
import com.digitalbluebird.notifications.domain.port.outbound.NotificationChannel
import com.digitalbluebird.notifications.domain.port.outbound.NotificationRepository
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * Delivers pending notifications through the [NotificationChannel], one at a time, marking each sent
 * only after the channel accepts it. Send-then-mark makes delivery at-least-once: a send that succeeds
 * but whose mark is lost (a crash) is retried, so a channel treats the notification id as an
 * idempotency key. Each notification is sent and marked independently — no shared transaction — so one
 * slow or failed send never blocks or rolls back the others; a failure just leaves that one pending
 * for the next run.
 *
 * Like the outbox relay, this is a single logical delivery worker: it assumes one running instance.
 * Fanning delivery across several instances would want `FOR UPDATE SKIP LOCKED` claiming — the same
 * per-consumer scaling concern the relay defers — rather than the plain pending scan here.
 */
class NotificationDispatcher(
    private val notifications: NotificationRepository,
    private val channel: NotificationChannel,
    private val clock: Clock,
) : DispatchNotificationsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun dispatchPending(limit: Int): Int {
        var sent = 0
        for (notification in notifications.findPending(limit)) {
            try {
                channel.send(notification)
                notifications.markSent(notification.id, clock.instant())
                sent++
            } catch (e: Exception) {
                // Leave it pending for the next run; one undeliverable notification must not block the batch.
                log.warn("notification {} could not be delivered, leaving it pending", notification.id, e)
            }
        }
        return sent
    }
}
