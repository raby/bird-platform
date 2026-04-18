package com.digitalbluebird.shared.infra

import com.digitalbluebird.shared.infra.outbox.JdbcOutboxRepository
import com.digitalbluebird.shared.infra.outbox.OutboxRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock
import javax.sql.DataSource

@Configuration
class SharedInfraConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun sharedJdbcTemplate(dataSource: DataSource): NamedParameterJdbcTemplate =
        NamedParameterJdbcTemplate(dataSource)

    @Bean
    fun outboxRepository(jdbc: NamedParameterJdbcTemplate): OutboxRepository =
        JdbcOutboxRepository(jdbc)
}
