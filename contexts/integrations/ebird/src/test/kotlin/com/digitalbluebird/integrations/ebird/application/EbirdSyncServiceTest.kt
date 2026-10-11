package com.digitalbluebird.integrations.ebird.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.integrations.ebird.domain.EbirdSubmission
import com.digitalbluebird.integrations.ebird.domain.SubmissionStatus
import com.digitalbluebird.integrations.ebird.domain.port.inbound.SyncSightingCommand
import com.digitalbluebird.integrations.ebird.domain.port.outbound.EbirdSubmissionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class EbirdSyncServiceTest {

    private val fixedAt = Instant.parse("2026-05-01T09:00:00Z")
    private val clock = Clock.fixed(fixedAt, ZoneOffset.UTC)
    private val repo = mockk<EbirdSubmissionRepository>(relaxed = true)
    private val service = EbirdSyncService(repo, clock)

    @Test
    fun `sync enqueues a pending submission built from the command`() {
        val captured = slot<EbirdSubmission>()
        every { repo.enqueue(capture(captured)) } returns true
        val sightingId = UUID.randomUUID().toString()

        service.sync(SyncSightingCommand(sightingId, "turdus-merula", 54.1, -2.8, fixedAt, 2))

        verify(exactly = 1) { repo.enqueue(any()) }
        val s = captured.captured
        assertThat(s.sightingId).isEqualTo(sightingId)
        assertThat(s.speciesId).isEqualTo("turdus-merula")
        assertThat(s.count).isEqualTo(2)
        assertThat(s.status).isEqualTo(SubmissionStatus.PENDING)
        assertThat(s.attempts).isEqualTo(0)
        assertThat(s.createdAt).isEqualTo(fixedAt)
    }
}
