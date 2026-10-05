package com.digitalbluebird.notifications.adapter.outbound.channel

import com.digitalbluebird.notifications.domain.Notification
import com.digitalbluebird.notifications.domain.port.outbound.NotificationChannel
import org.slf4j.LoggerFactory

/**
 * The stub delivery channel: it "sends" a notification by logging it. A real deployment swaps this for
 * an email / SMS / push / webhook adapter behind the same [NotificationChannel] port — nothing else in
 * the context changes.
 */
class LoggingNotificationChannel : NotificationChannel {
    private val log = LoggerFactory.getLogger("com.digitalbluebird.notifications.channel")

    override fun send(notification: Notification) {
        log.info(
            "NOTIFY [{}] to {}: {} — {} (source event {})",
            notification.kind,
            notification.recipient,
            notification.subject,
            notification.body,
            notification.sourceEventId,
        )
    }
}
