package com.digitalbluebird.integrations.ebird.application

import com.digitalbluebird.integrations.ebird.domain.EbirdSubmission
import com.digitalbluebird.integrations.ebird.domain.port.inbound.DispatchEbirdSubmissionsUseCase
import com.digitalbluebird.integrations.ebird.domain.port.outbound.EbirdGateway
import com.digitalbluebird.integrations.ebird.domain.port.outbound.EbirdSubmissionRepository
import com.digitalbluebird.integrations.ebird.domain.port.outbound.SightingToSync
import com.digitalbluebird.shared.infra.resilience.CircuitBreaker
import com.digitalbluebird.shared.infra.resilience.CircuitOpenException
import com.digitalbluebird.shared.infra.resilience.retry
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

/**
 * The deliver side: push PENDING submissions to eBird through the gateway, wrapped in resilience.
 *
 * Each submission is attempted as `breaker.execute { retry { gateway.submit } }` — the retry absorbs a
 * transient blip as one logical call, and the breaker judges the dependency's health across calls. On
 * success the submission is marked SUBMITTED. A real failure records an attempt and, once the attempt
 * budget is spent, dead-letters the submission as FAILED. A circuit-open fast-fail is *not* counted
 * against the submission (eBird being down is not its fault): the run simply stops and tries again
 * next poll, by which time the breaker's reset window may have elapsed and it will probe for recovery.
 *
 * Submit-then-mark makes delivery at-least-once: a submission accepted by eBird whose mark is then lost
 * (a crash) is re-sent next run, so the gateway carries the sighting id as eBird's idempotency key.
 * Like the outbox relay, this is a single logical delivery worker that assumes one running instance;
 * scaling across several would want `FOR UPDATE SKIP LOCKED` claiming rather than the plain pending scan.
 */
class EbirdSubmissionDispatcher(
    private val submissions: EbirdSubmissionRepository,
    private val gateway: EbirdGateway,
    private val breaker: CircuitBreaker,
    private val clock: Clock,
    private val maxAttempts: Int = 5,
    private val retriesPerAttempt: Int = 3,
    private val retryBackoff: Duration = Duration.ofMillis(100),
) : DispatchEbirdSubmissionsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun dispatchPending(limit: Int): Int {
        var submitted = 0
        for (submission in submissions.findPending(limit)) {
            try {
                breaker.execute {
                    retry(maxAttempts = retriesPerAttempt, delay = retryBackoff) {
                        gateway.submit(submission.toSync())
                    }
                }
                submissions.update(submission.submitted(clock.instant()))
                submitted++
            } catch (e: CircuitOpenException) {
                // eBird is down and the breaker is fast-failing. Leave this one PENDING without burning
                // an attempt, and stop — every other submission in the batch would fast-fail too.
                log.warn("eBird circuit is open; deferring remaining submissions to the next run")
                break
            } catch (e: Exception) {
                val failed = submission.failedAttempt(e.message ?: e.javaClass.simpleName, clock.instant(), maxAttempts)
                submissions.update(failed)
                log.warn(
                    "eBird submission {} failed (attempt {} of {}), now {}",
                    submission.sightingId, failed.attempts, maxAttempts, failed.status, e,
                )
            }
        }
        return submitted
    }

    private fun EbirdSubmission.toSync() = SightingToSync(
        sightingId = sightingId,
        speciesId = speciesId,
        latitude = latitude,
        longitude = longitude,
        observedAt = observedAt,
        count = count,
    )
}
