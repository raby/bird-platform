package com.digitalbluebird.shared.infra

import com.digitalbluebird.shared.infra.outbox.JdbcOutboxRepository
import com.digitalbluebird.shared.infra.outbox.OutboxHandler
import com.digitalbluebird.shared.infra.outbox.OutboxPoller
import com.digitalbluebird.shared.infra.outbox.OutboxRelay
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

    // Collects every OutboxHandler bean in the context (e.g. the bookings hide-availability
    // projector). With no handlers, Spring injects an empty list and the relay just marks entries
    // published.
    @Bean
    fun outboxRelay(
        outbox: OutboxRepository,
        handlers: List<OutboxHandler>,
        clock: Clock,
    ): OutboxRelay = OutboxRelay(outbox, handlers, clock)

    @Bean
    fun outboxPoller(relay: OutboxRelay): OutboxPoller = OutboxPoller(relay)
}
