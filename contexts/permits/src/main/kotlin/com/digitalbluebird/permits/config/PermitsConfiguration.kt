package com.digitalbluebird.permits.config

import com.digitalbluebird.permits.adapter.outbound.persistence.JdbcVisitorPermitRepository
import com.digitalbluebird.permits.application.VisitorPermitService
import com.digitalbluebird.permits.domain.port.outbound.VisitorPermitRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock

@Configuration
class PermitsConfiguration {

    @Bean
    fun visitorPermitRepository(jdbc: NamedParameterJdbcTemplate): VisitorPermitRepository =
        JdbcVisitorPermitRepository(jdbc)

    // Single concrete bean behind the IssueVisitorPermitUseCase port (mirroring the other contexts),
    // so the saga's port injection stays unambiguous.
    @Bean
    fun visitorPermitService(permits: VisitorPermitRepository, clock: Clock): VisitorPermitService =
        VisitorPermitService(permits, clock)
}
