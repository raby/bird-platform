package com.digitalbluebird.shared.infra.outbox

interface OutboxPublisher {
    fun publish(entry: OutboxEntry)
}
