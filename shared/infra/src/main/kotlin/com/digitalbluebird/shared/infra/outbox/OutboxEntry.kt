package com.digitalbluebird.shared.infra.outbox

import java.time.Instant
import java.util.UUID

data class OutboxEntry(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val occurredAt: Instant,
    val publishedAt: Instant? = null,
) {
    val isPublished: Boolean get() = publishedAt != null
}
