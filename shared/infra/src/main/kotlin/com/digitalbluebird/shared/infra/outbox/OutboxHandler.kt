package com.digitalbluebird.shared.infra.outbox

/**
 * Consumes an outbox entry when the relay drains it. Handlers must be **idempotent**: the relay
 * delivers at least once (an entry stays unpublished and is retried if any handler throws before it
 * is marked published), so processing the same entry twice must be safe. A handler that does not
 * care about an entry's [OutboxEntry.eventType] simply ignores it.
 */
fun interface OutboxHandler {
    fun handle(entry: OutboxEntry)
}
