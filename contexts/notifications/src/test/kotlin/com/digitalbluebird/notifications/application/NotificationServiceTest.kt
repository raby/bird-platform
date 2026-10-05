package com.digitalbluebird.notifications.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.notifications.domain.Notification
import com.digitalbluebird.notifications.domain.NotificationStatus
import com.digitalbluebird.notifications.domain.port.inbound.NotificationRequest
import com.digitalbluebird.notifications.domain.port.outbound.NotificationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class NotificationServiceTest {

    private val fixedAt = Instant.parse("2026-05-01T09:00:00Z")
    private val clock = Clock.fixed(fixedAt, ZoneOffset.UTC)
    private val repo = mockk<NotificationRepository>(relaxed = true)
    private val service = NotificationService(repo, clock)

    @Test
    fun `notify enqueues a pending notification built from the request`() {
        val sourceEventId = UUID.randomUUID()
        val captured = slot<Notification>()
        every { repo.enqueue(capture(captured)) } returns true

        service.notify(
            NotificationRequest(
                kind = "BOOKING_CONFIRMED",
                recipient = "observer-1",
                subject = "Confirmed",
                body = "Your booking is confirmed.",
                sourceEventId = sourceEventId,
            ),
        )

        verify(exactly = 1) { repo.enqueue(any()) }
        val n = captured.captured
        assertThat(n.kind).isEqualTo("BOOKING_CONFIRMED")
        assertThat(n.recipient).isEqualTo("observer-1")
        assertThat(n.subject).isEqualTo("Confirmed")
        assertThat(n.body).isEqualTo("Your booking is confirmed.")
        assertThat(n.sourceEventId).isEqualTo(sourceEventId)
        assertThat(n.status).isEqualTo(NotificationStatus.PENDING)
        assertThat(n.createdAt).isEqualTo(fixedAt)
    }

    @Test
    fun `recent delegates to the repository`() {
        every { repo.recent(10) } returns emptyList()
        assertThat(service.recent(10)).isEqualTo(emptyList())
        verify { repo.recent(10) }
    }
}
