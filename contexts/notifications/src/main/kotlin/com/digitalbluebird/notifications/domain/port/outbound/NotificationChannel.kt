package com.digitalbluebird.notifications.domain.port.outbound

import com.digitalbluebird.notifications.domain.Notification

/**
 * Delivers a notification to the outside world — email, SMS, push, a webhook. Implementations throw on
 * failure; the dispatcher then leaves the notification pending to retry later. Because delivery is
 * at-least-once, a real channel should use the notification's id as an idempotency key.
 */
fun interface NotificationChannel {
    fun send(notification: Notification)
}
