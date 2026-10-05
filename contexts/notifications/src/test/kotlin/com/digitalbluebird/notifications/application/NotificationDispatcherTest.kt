package com.digitalbluebird.notifications.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.notifications.domain.Notification
import com.digitalbluebird.notifications.domain.NotificationId
import com.digitalbluebird.notifications.domain.port.outbound.NotificationChannel
import com.digitalbluebird.notifications.domain.port.outbound.NotificationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class NotificationDispatcherTest {

    private val at = Instant.parse("2026-05-01T09:00:00Z")
    private val clock = Clock.fixed(at, ZoneOffset.UTC)

    private fun pending(kind: String) = Notification.pending(
        id = NotificationId.random(),
        kind = kind,
        recipient = "observer-1",
        subject = "s",
        body = "b",
        sourceEventId = UUID.randomUUID(),
        createdAt = at,
    )

    @Test
    fun `dispatch sends each pending notification and marks it sent`() {
        val a = pending("A")
        val b = pending("B")
        val repo = mockk<NotificationRepository>(relaxed = true)
        val channel = mockk<NotificationChannel>(relaxed = true)
        every { repo.findPending(any()) } returns listOf(a, b)

        val sent = NotificationDispatcher(repo, channel, clock).dispatchPending(100)

        assertThat(sent).isEqualTo(2)
        verify { channel.send(a) }
        verify { channel.send(b) }
        verify { repo.markSent(a.id, at) }
        verify { repo.markSent(b.id, at) }
    }

    @Test
    fun `a channel failure leaves that notification pending and does not stop the batch`() {
        val bad = pending("BAD")
        val good = pending("GOOD")
        val repo = mockk<NotificationRepository>(relaxed = true)
        val channel = mockk<NotificationChannel>()
        every { channel.send(bad) } throws RuntimeException("sink down")
        every { channel.send(good) } returns Unit
        every { repo.findPending(any()) } returns listOf(bad, good)

        val sent = NotificationDispatcher(repo, channel, clock).dispatchPending(100)

        assertThat(sent).isEqualTo(1)
        verify(exactly = 0) { repo.markSent(bad.id, any()) }
        verify { repo.markSent(good.id, at) }
    }
}
