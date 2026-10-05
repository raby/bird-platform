package com.digitalbluebird.notifications.adapter.inbound.web

import com.digitalbluebird.notifications.domain.Notification
import java.time.Instant

data class NotificationResponse(
    val id: String,
    val kind: String,
    val recipient: String,
    val subject: String,
    val body: String,
    val sourceEventId: String,
    val status: String,
    val createdAt: Instant,
    val sentAt: Instant?,
) {
    companion object {
        fun from(n: Notification) = NotificationResponse(
            id = n.id.toString(),
            kind = n.kind,
            recipient = n.recipient,
            subject = n.subject,
            body = n.body,
            sourceEventId = n.sourceEventId.toString(),
            status = n.status.name,
            createdAt = n.createdAt,
            sentAt = n.sentAt,
        )
    }
}
