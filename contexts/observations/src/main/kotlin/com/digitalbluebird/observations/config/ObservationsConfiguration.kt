package com.digitalbluebird.observations.config

import com.digitalbluebird.observations.adapter.outbound.persistence.JdbcSightingRepository
import com.digitalbluebird.observations.application.SightingService
import com.digitalbluebird.observations.domain.port.inbound.FindSightingUseCase
import com.digitalbluebird.observations.domain.port.inbound.RecordSightingUseCase
import com.digitalbluebird.observations.domain.port.outbound.SightingRepository
import com.digitalbluebird.shared.infra.outbox.OutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock

@Configuration
class ObservationsConfiguration {

    @Bean
    fun sightingRepository(jdbc: NamedParameterJdbcTemplate): SightingRepository =
        JdbcSightingRepository(jdbc)

    @Bean
    fun sightingService(
        sightings: SightingRepository,
        outbox: OutboxRepository,
        clock: Clock,
        objectMapper: ObjectMapper,
    ): SightingService = SightingService(sightings, outbox, clock, objectMapper)

    @Bean
    fun recordSightingUseCase(sightingService: SightingService): RecordSightingUseCase = sightingService

    @Bean
    fun findSightingUseCase(sightingService: SightingService): FindSightingUseCase = sightingService
}
