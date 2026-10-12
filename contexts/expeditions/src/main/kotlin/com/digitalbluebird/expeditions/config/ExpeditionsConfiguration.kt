package com.digitalbluebird.expeditions.config

import com.digitalbluebird.expeditions.adapter.outbound.persistence.JdbcExpeditionRepository
import com.digitalbluebird.expeditions.application.ExpeditionService
import com.digitalbluebird.expeditions.domain.port.outbound.ExpeditionRepository
import com.digitalbluebird.expeditions.domain.port.outbound.HideBookingGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock

@Configuration
class ExpeditionsConfiguration {

    @Bean
    fun expeditionRepository(jdbc: NamedParameterJdbcTemplate): ExpeditionRepository =
        JdbcExpeditionRepository(jdbc)

    // One concrete bean behind the three inbound ports (PlanExpeditionUseCase / ViewExpeditionUseCase /
    // HandleBookingCancelledUseCase): the controller injects the first two, the api process manager the
    // third, each resolving to this single instance. The HideBookingGateway impl is supplied by the api
    // composition root (the only place allowed to depend on both expeditions and bookings).
    @Bean
    fun expeditionService(
        expeditions: ExpeditionRepository,
        hides: HideBookingGateway,
        clock: Clock,
    ): ExpeditionService = ExpeditionService(expeditions, hides, clock)
}
