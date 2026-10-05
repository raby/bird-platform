package com.digitalbluebird.notifications.domain.port.inbound

import java.util.UUID

/**
 * Raises a notification from a source event. Idempotent on [NotificationRequest.sourceEventId]: the
 * notifications context consumes an at-least-once stream, so the same event may be delivered more than
 * once, and enqueuing the same source event twice must still yield a single notification.
 */
fun interface NotifyUseCase {
    fun notify(request: NotificationRequest)
}

/**
 * What to notify about. [sourceEventId] is the id of the originating event and doubles as the
 * deduplication key. [recipient] is who to reach (an observer id here; a real system resolves it to an
 * address). [kind] is a short machine tag for filtering, e.g. `BOOKING_CONFIRMED`.
 */
data class NotificationRequest(
    val kind: String,
    val recipient: String,
    val subject: String,
    val body: String,
    val sourceEventId: UUID,
)
