package com.digitalbluebird.shared.infra.outbox

import java.time.Instant
import java.util.UUID

interface OutboxRepository {
    fun save(entry: OutboxEntry)
    fun findUnpublished(limit: Int): List<OutboxEntry>
    fun markPublished(id: UUID, at: Instant)
}
