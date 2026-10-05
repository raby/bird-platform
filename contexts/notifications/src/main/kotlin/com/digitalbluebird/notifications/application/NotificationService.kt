package com.digitalbluebird.notifications.application

import com.digitalbluebird.notifications.domain.Notification
import com.digitalbluebird.notifications.domain.NotificationId
import com.digitalbluebird.notifications.domain.port.inbound.NotificationRequest
import com.digitalbluebird.notifications.domain.port.inbound.NotifyUseCase
import com.digitalbluebird.notifications.domain.port.inbound.ViewNotificationsUseCase
import com.digitalbluebird.notifications.domain.port.outbound.NotificationRepository
import java.time.Clock

/**
 * Raises and reads notifications. [notify] enqueues one per source event; the repository deduplicates
 * on the source event id, so the at-least-once event stream this context consumes yields at most one
 * notification per event — the consume half of the inbox pattern.
 */
class NotificationService(
    private val notifications: NotificationRepository,
    private val clock: Clock,
) : NotifyUseCase, ViewNotificationsUseCase {

    override fun notify(request: NotificationRequest) {
        val notification = Notification.pending(
            id = NotificationId.random(),
            kind = request.kind,
            recipient = request.recipient,
            subject = request.subject,
            body = request.body,
            sourceEventId = request.sourceEventId,
            createdAt = clock.instant(),
        )
        notifications.enqueue(notification)
    }

    override fun recent(limit: Int): List<Notification> = notifications.recent(limit)
}
