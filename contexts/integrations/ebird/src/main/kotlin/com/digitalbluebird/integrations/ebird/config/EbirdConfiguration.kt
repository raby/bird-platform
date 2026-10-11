package com.digitalbluebird.integrations.ebird.config

import com.digitalbluebird.integrations.ebird.adapter.inbound.schedule.EbirdDispatchPoller
import com.digitalbluebird.integrations.ebird.adapter.outbound.ebird.StubEbirdGateway
import com.digitalbluebird.integrations.ebird.adapter.outbound.persistence.JdbcEbirdSubmissionRepository
import com.digitalbluebird.integrations.ebird.application.EbirdQueryService
import com.digitalbluebird.integrations.ebird.application.EbirdSubmissionDispatcher
import com.digitalbluebird.integrations.ebird.application.EbirdSyncService
import com.digitalbluebird.integrations.ebird.domain.port.inbound.DispatchEbirdSubmissionsUseCase
import com.digitalbluebird.integrations.ebird.domain.port.outbound.EbirdGateway
import com.digitalbluebird.integrations.ebird.domain.port.outbound.EbirdSubmissionRepository
import com.digitalbluebird.shared.infra.resilience.CircuitBreaker
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock
import java.time.Duration

@Configuration
class EbirdConfiguration {

    @Bean
    fun ebirdSubmissionRepository(jdbc: NamedParameterJdbcTemplate): EbirdSubmissionRepository =
        JdbcEbirdSubmissionRepository(jdbc)

    @Bean
    fun ebirdGateway(): EbirdGateway = StubEbirdGateway()

    // One shared breaker instance: the dispatcher drives its state, the query service reads it.
    @Bean
    fun ebirdCircuitBreaker(
        clock: Clock,
        @Value("\${bird.ebird.circuit.failure-threshold:3}") failureThreshold: Int,
        @Value("\${bird.ebird.circuit.reset-timeout-ms:10000}") resetTimeoutMs: Long,
    ): CircuitBreaker = CircuitBreaker(failureThreshold, Duration.ofMillis(resetTimeoutMs), clock, "ebird")

    // One concrete bean per port (the SyncSightingUseCase / ViewEbirdUseCase / DispatchEbirdSubmissions
    // injections each resolve to a single instance) — do not also register interface-typed beans.
    @Bean
    fun ebirdSyncService(submissions: EbirdSubmissionRepository, clock: Clock): EbirdSyncService =
        EbirdSyncService(submissions, clock)

    @Bean
    fun ebirdSubmissionDispatcher(
        submissions: EbirdSubmissionRepository,
        gateway: EbirdGateway,
        breaker: CircuitBreaker,
        clock: Clock,
        @Value("\${bird.ebird.max-attempts:5}") maxAttempts: Int,
    ): EbirdSubmissionDispatcher =
        EbirdSubmissionDispatcher(submissions, gateway, breaker, clock, maxAttempts)

    @Bean
    fun ebirdQueryService(submissions: EbirdSubmissionRepository, breaker: CircuitBreaker): EbirdQueryService =
        EbirdQueryService(submissions, breaker)

    @Bean
    fun ebirdDispatchPoller(dispatcher: DispatchEbirdSubmissionsUseCase): EbirdDispatchPoller =
        EbirdDispatchPoller(dispatcher)
}
