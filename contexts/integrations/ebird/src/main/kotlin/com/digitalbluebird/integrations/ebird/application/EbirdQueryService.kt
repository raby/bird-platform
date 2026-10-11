package com.digitalbluebird.integrations.ebird.application

import com.digitalbluebird.integrations.ebird.domain.EbirdSubmission
import com.digitalbluebird.integrations.ebird.domain.port.inbound.ViewEbirdUseCase
import com.digitalbluebird.integrations.ebird.domain.port.outbound.EbirdSubmissionRepository
import com.digitalbluebird.shared.infra.resilience.CircuitBreaker

/** Read side: the recent submissions and the live circuit-breaker state, for observability. */
class EbirdQueryService(
    private val submissions: EbirdSubmissionRepository,
    private val breaker: CircuitBreaker,
) : ViewEbirdUseCase {

    override fun recent(limit: Int): List<EbirdSubmission> = submissions.recent(limit)

    override fun circuitState(): String = breaker.currentState.name
}
