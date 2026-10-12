package com.digitalbluebird.reserves.config

import com.digitalbluebird.reserves.adapter.outbound.persistence.JdbcReserveRepository
import com.digitalbluebird.reserves.application.ReserveService
import com.digitalbluebird.reserves.domain.port.outbound.ReserveRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

@Configuration
class ReservesConfiguration {

    @Bean
    fun reserveRepository(jdbc: NamedParameterJdbcTemplate): ReserveRepository =
        JdbcReserveRepository(jdbc)

    // Single concrete bean behind FindReservesUseCase (the controller's port), per the one-bean-per-port
    // convention.
    @Bean
    fun reserveService(reserves: ReserveRepository): ReserveService = ReserveService(reserves)
}
