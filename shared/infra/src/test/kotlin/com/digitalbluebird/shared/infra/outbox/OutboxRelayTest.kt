package com.digitalbluebird.shared.infra.outbox

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class OutboxRelayTest {

    private val now: Instant = Instant.parse("2026-06-01T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val outbox: OutboxRepository = mockk(relaxed = true)

    private fun entry(type: String = "BookingConfirmed") = OutboxEntry(
        id = UUID.randomUUID(),
        aggregateType = "Booking",
        aggregateId = UUID.randomUUID().toString(),
        eventType = type,
        payload = "{}",
        occurredAt = now,
    )

    @Test
    fun `dispatches each unpublished entry to every handler, then marks it published`() {
        val e1 = entry("BookingConfirmed")
        val e2 = entry("BookingCancelled")
        every { outbox.findUnpublished(any()) } returns listOf(e1, e2)
        val handlerA: OutboxHandler = mockk(relaxed = true)
        val handlerB: OutboxHandler = mockk(relaxed = true)
        val relay = OutboxRelay(outbox, listOf(handlerA, handlerB), clock)

        val processed = relay.drainOnce(100)

        assertThat(processed).isEqualTo(2)
        verify(exactly = 1) { handlerA.handle(e1) }
        verify(exactly = 1) { handlerA.handle(e2) }
        verify(exactly = 1) { handlerB.handle(e1) }
        verify(exactly = 1) { handlerB.handle(e2) }
        verify(exactly = 1) { outbox.markPublished(e1.id, now) }
        verify(exactly = 1) { outbox.markPublished(e2.id, now) }
    }

    @Test
    fun `returns zero and marks nothing when the outbox is empty`() {
        every { outbox.findUnpublished(any()) } returns emptyList()
        val relay = OutboxRelay(outbox, listOf(mockk(relaxed = true)), clock)

        assertThat(relay.drainOnce(100)).isEqualTo(0)
        verify(exactly = 0) { outbox.markPublished(any(), any()) }
    }

    @Test
    fun `a throwing handler propagates so the batch transaction rolls back and the entry is retried`() {
        val e1 = entry()
        every { outbox.findUnpublished(any()) } returns listOf(e1)
        val handler: OutboxHandler = mockk()
        every { handler.handle(e1) } throws IllegalStateException("projection failed")
        val relay = OutboxRelay(outbox, listOf(handler), clock)

        assertThrows<IllegalStateException> { relay.drainOnce(100) }
        verify(exactly = 0) { outbox.markPublished(any(), any()) }
    }
}
