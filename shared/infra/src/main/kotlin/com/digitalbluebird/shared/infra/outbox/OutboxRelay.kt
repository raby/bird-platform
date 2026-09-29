package com.digitalbluebird.shared.infra.outbox

import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Drains unpublished outbox entries and dispatches each to every handler, then marks it published —
 * the read side of the transactional-outbox pattern that [OutboxRepository.save] writes to.
 *
 * Delivery is **at-least-once**: the whole drain runs in one transaction, so a handler that throws
 * rolls the batch back, leaving the entry unpublished for the next drain (handlers must therefore be
 * idempotent). [OutboxPoller] calls [drainOnce] on a schedule in production; tests call it directly
 * for determinism. Declared `open` so Spring can apply the `@Transactional` proxy.
 *
 * An entry's single `published_at` flag is shared by all handlers, so this fits **one logical
 * consumer** — the in-process read-model projections. Fanning the same events out to an independent
 * external sink (a Kafka/SNS relay) later wants per-consumer offsets, which is the separate
 * outbox-publisher slice, not this one.
 */
open class OutboxRelay(
    private val outbox: OutboxRepository,
    private val handlers: List<OutboxHandler>,
    private val clock: Clock,
) {
    @Transactional
    open fun drainOnce(batchSize: Int): Int {
        val entries = outbox.findUnpublished(batchSize)
        for (entry in entries) {
            handlers.forEach { it.handle(entry) }
            outbox.markPublished(entry.id, clock.instant())
        }
        return entries.size
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 100
    }
}
