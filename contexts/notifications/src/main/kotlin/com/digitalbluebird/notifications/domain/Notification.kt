package com.digitalbluebird.notifications.domain

import java.time.Instant
import java.util.UUID

/**
 * A notification raised from a domain event elsewhere on the platform (a booking confirmed or
 * cancelled, say) and later delivered through a notification channel.
 *
 * [sourceEventId] is the id of the event that produced it — the idempotency (inbox) key. The
 * notifications context consumes an at-least-once event stream, so the same event may arrive more
 * than once; enqueuing is deduplicated on this id, so one event yields at most one notification.
 */
data class Notification(
    val id: NotificationId,
    val kind: String,
    val recipient: String,
    val subject: String,
    val body: String,
    val sourceEventId: UUID,
    val status: NotificationStatus,
    val createdAt: Instant,
    val sentAt: Instant? = null,
) {
    /** Mark this notification delivered. Idempotent: marking an already-sent one changes nothing. */
    fun markSent(at: Instant): Notification =
        if (status == NotificationStatus.SENT) this else copy(status = NotificationStatus.SENT, sentAt = at)

    companion object {
        /** A fresh, undelivered notification. */
        fun pending(
            id: NotificationId,
            kind: String,
            recipient: String,
            subject: String,
            body: String,
            sourceEventId: UUID,
            createdAt: Instant,
        ): Notification =
            Notification(id, kind, recipient, subject, body, sourceEventId, NotificationStatus.PENDING, createdAt)
    }
}

enum class NotificationStatus { PENDING, SENT }
