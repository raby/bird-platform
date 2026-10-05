package com.digitalbluebird.notifications.domain.port.inbound

/**
 * Delivers pending notifications through the channel and marks them sent, up to [limit] at a time.
 * Delivery is at-least-once: a send that succeeds but whose mark-sent is then lost (a crash) is
 * retried on the next run, so a channel should treat a notification's id as an idempotency key.
 * Returns the number delivered.
 */
fun interface DispatchNotificationsUseCase {
    fun dispatchPending(limit: Int): Int
}
