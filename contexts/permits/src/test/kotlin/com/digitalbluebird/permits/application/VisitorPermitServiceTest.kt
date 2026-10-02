package com.digitalbluebird.permits.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.permits.domain.PermitDecision
import com.digitalbluebird.permits.domain.port.outbound.ReserveOutcome
import com.digitalbluebird.permits.domain.port.outbound.VisitorPermitRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class VisitorPermitServiceTest {

    private val now: Instant = Instant.parse("2026-05-01T09:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val permits: VisitorPermitRepository = mockk(relaxed = true)
    private val service = VisitorPermitService(permits, clock)

    private val bookingId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val hideId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val day: LocalDate = LocalDate.of(2026, 5, 1)

    @Test
    fun `issues and records when the reservation fits under the cap`() {
        every { permits.isIssued(bookingId) } returns false
        every { permits.reserve(hideId, day, 2) } returns ReserveOutcome.RESERVED

        assertThat(service.issueFor(bookingId, hideId, day, 2)).isEqualTo(PermitDecision.Issued)
        verify(exactly = 1) { permits.recordIssuance(bookingId, hideId, day, 2, now) }
    }

    @Test
    fun `issues without recording when no cap is configured`() {
        every { permits.isIssued(bookingId) } returns false
        every { permits.reserve(hideId, day, 2) } returns ReserveOutcome.NO_CAP

        assertThat(service.issueFor(bookingId, hideId, day, 2)).isEqualTo(PermitDecision.Issued)
        verify(exactly = 0) { permits.recordIssuance(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `is exhausted when the cap is full`() {
        every { permits.isIssued(bookingId) } returns false
        every { permits.reserve(hideId, day, 5) } returns ReserveOutcome.EXHAUSTED

        assertThat(service.issueFor(bookingId, hideId, day, 5)).isEqualTo(PermitDecision.Exhausted)
        verify(exactly = 0) { permits.recordIssuance(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `is idempotent - a re-issue returns Issued without reserving again`() {
        every { permits.isIssued(bookingId) } returns true

        assertThat(service.issueFor(bookingId, hideId, day, 2)).isEqualTo(PermitDecision.Issued)
        verify(exactly = 0) { permits.reserve(any(), any(), any()) }
        verify(exactly = 0) { permits.recordIssuance(any(), any(), any(), any(), any()) }
    }
}
