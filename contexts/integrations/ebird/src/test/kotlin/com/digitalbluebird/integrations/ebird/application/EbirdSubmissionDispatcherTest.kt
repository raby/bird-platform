package com.digitalbluebird.integrations.ebird.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.integrations.ebird.adapter.outbound.ebird.EbirdUnavailableException
import com.digitalbluebird.integrations.ebird.domain.EbirdSubmission
import com.digitalbluebird.integrations.ebird.domain.SubmissionStatus
import com.digitalbluebird.integrations.ebird.domain.port.outbound.EbirdGateway
import com.digitalbluebird.integrations.ebird.domain.port.outbound.EbirdSubmissionRepository
import com.digitalbluebird.shared.infra.resilience.CircuitBreaker
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class EbirdSubmissionDispatcherTest {

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    private val clock = MutableClock(Instant.parse("2026-05-01T00:00:00Z"))
    private val repo = mockk<EbirdSubmissionRepository>(relaxed = true)
    private val gateway = mockk<EbirdGateway>()

    private fun pending() = EbirdSubmission.pending(
        sightingId = UUID.randomUUID().toString(),
        speciesId = "turdus-merula",
        latitude = 54.1,
        longitude = -2.8,
        observedAt = clock.now,
        count = 1,
        at = clock.now,
    )

    private fun dispatcher(
        breaker: CircuitBreaker = CircuitBreaker(5, Duration.ofSeconds(30), clock, "test"),
        maxAttempts: Int = 5,
        retries: Int = 1,
    ) = EbirdSubmissionDispatcher(repo, gateway, breaker, clock, maxAttempts, retries, Duration.ZERO)

    @Test
    fun `submits pending submissions and marks them SUBMITTED`() {
        val a = pending()
        val b = pending()
        every { repo.findPending(any()) } returns listOf(a, b)
        every { gateway.submit(any()) } returns Unit
        val updates = mutableListOf<EbirdSubmission>()
        every { repo.update(capture(updates)) } returns Unit

        val submitted = dispatcher().dispatchPending(10)

        assertThat(submitted).isEqualTo(2)
        assertThat(updates.map { it.status })
            .isEqualTo(listOf(SubmissionStatus.SUBMITTED, SubmissionStatus.SUBMITTED))
    }

    @Test
    fun `a transient failure is retried and then succeeds`() {
        val a = pending()
        every { repo.findPending(any()) } returns listOf(a)
        every { gateway.submit(any()) } throws RuntimeException("blip") andThen Unit
        val updates = mutableListOf<EbirdSubmission>()
        every { repo.update(capture(updates)) } returns Unit

        val submitted = dispatcher(retries = 2).dispatchPending(10)

        assertThat(submitted).isEqualTo(1)
        assertThat(updates.single().status).isEqualTo(SubmissionStatus.SUBMITTED)
    }

    @Test
    fun `a real failure records an attempt and stays pending below the max`() {
        val a = pending()
        every { repo.findPending(any()) } returns listOf(a)
        every { gateway.submit(any()) } throws EbirdUnavailableException("down")
        val updates = mutableListOf<EbirdSubmission>()
        every { repo.update(capture(updates)) } returns Unit

        dispatcher(maxAttempts = 3).dispatchPending(10)

        assertThat(updates.single().status).isEqualTo(SubmissionStatus.PENDING)
        assertThat(updates.single().attempts).isEqualTo(1)
    }

    @Test
    fun `reaching the max attempts dead-letters the submission as FAILED`() {
        val a = pending()
        every { repo.findPending(any()) } returns listOf(a)
        every { gateway.submit(any()) } throws EbirdUnavailableException("down")
        val updates = mutableListOf<EbirdSubmission>()
        every { repo.update(capture(updates)) } returns Unit

        dispatcher(maxAttempts = 1).dispatchPending(10)

        assertThat(updates.single().status).isEqualTo(SubmissionStatus.FAILED)
    }

    @Test
    fun `when the breaker opens, remaining submissions are left pending without burning an attempt`() {
        val a = pending()
        val b = pending()
        every { repo.findPending(any()) } returns listOf(a, b)
        every { gateway.submit(any()) } throws EbirdUnavailableException("down")
        val updates = mutableListOf<EbirdSubmission>()
        every { repo.update(capture(updates)) } returns Unit

        // Threshold 1: a's failure opens the breaker, so b fast-fails (circuit open) and is left alone.
        dispatcher(breaker = CircuitBreaker(1, Duration.ofSeconds(30), clock, "test")).dispatchPending(10)

        assertThat(updates.size).isEqualTo(1)
        assertThat(updates.single().sightingId).isEqualTo(a.sightingId)
        assertThat(updates.single().attempts).isEqualTo(1)
    }
}
