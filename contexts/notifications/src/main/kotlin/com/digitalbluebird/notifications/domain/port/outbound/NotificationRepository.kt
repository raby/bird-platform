package com.digitalbluebird.notifications.domain.port.outbound

import com.digitalbluebird.notifications.domain.Notification
import com.digitalbluebird.notifications.domain.NotificationId
import java.time.Instant

interface NotificationRepository {
    /**
     * Persist a new notification, deduplicated on its source event id. Returns true if it was stored,
     * false if a notification for that source event already existed — so a re-delivered event is
     * silently ignored. This is the inbox half of the idempotent-consumer pattern.
     */
    fun enqueue(notification: Notification): Boolean

    /** The oldest pending notifications, up to [limit], for delivery. */
    fun findPending(limit: Int): List<Notification>

    fun markSent(id: NotificationId, at: Instant)

    /** The most recent notifications, newest first, up to [limit]. */
    fun recent(limit: Int): List<Notification>
}
