package com.digitalbluebird.integrations.ebird.application

import com.digitalbluebird.integrations.ebird.domain.EbirdSubmission
import com.digitalbluebird.integrations.ebird.domain.port.inbound.SyncSightingCommand
import com.digitalbluebird.integrations.ebird.domain.port.inbound.SyncSightingUseCase
import com.digitalbluebird.integrations.ebird.domain.port.outbound.EbirdSubmissionRepository
import java.time.Clock

/**
 * The consume side: queue a recorded sighting for syncing. The repository deduplicates on sightingId,
 * so the at-least-once outbox delivering SightingCreated more than once still yields a single PENDING
 * submission. The actual call to eBird is the dispatcher's job, kept off this path so a slow or down
 * eBird never blocks sighting ingestion.
 */
class EbirdSyncService(
    private val submissions: EbirdSubmissionRepository,
    private val clock: Clock,
) : SyncSightingUseCase {

    override fun sync(command: SyncSightingCommand) {
        val submission = EbirdSubmission.pending(
            sightingId = command.sightingId,
            speciesId = command.speciesId,
            latitude = command.latitude,
            longitude = command.longitude,
            observedAt = command.observedAt,
            count = command.count,
            at = clock.instant(),
        )
        submissions.enqueue(submission)
    }
}
