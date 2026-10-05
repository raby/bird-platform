package com.digitalbluebird.notifications.domain.port.inbound

import com.digitalbluebird.notifications.domain.Notification

/** Reads the most recent notifications, newest first — backs the read endpoint. */
fun interface ViewNotificationsUseCase {
    fun recent(limit: Int): List<Notification>
}
