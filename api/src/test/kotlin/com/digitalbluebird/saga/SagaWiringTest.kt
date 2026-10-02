package com.digitalbluebird.saga

import com.digitalbluebird.bookings.config.BookingsConfiguration
import com.digitalbluebird.permits.config.PermitsConfiguration
import com.digitalbluebird.shared.infra.SharedInfraConfiguration
import com.digitalbluebird.shared.infra.outbox.OutboxHandler
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier
import javax.sql.DataSource

/**
 * Guards the cross-context wiring of the visitor-permit saga: with the shared infra, bookings and
 * permits configurations present, the saga resolves its permits + bookings inbound ports and is
 * collected as an [OutboxHandler], so the shared relay drives it alongside the read-model projectors.
 * The api WiringTest cannot cover this (it does not load the permits context), so it lives here. No
 * Docker needed — the DataSource is mocked and never touched at wiring time.
 */
class SagaWiringTest {

    private val runner = ApplicationContextRunner()
        .withBean(DataSource::class.java, Supplier { mockk<DataSource>(relaxed = true) })
        .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
        .withUserConfiguration(
            SharedInfraConfiguration::class.java,
            BookingsConfiguration::class.java,
            PermitsConfiguration::class.java,
            SagaConfiguration::class.java,
        )

    @Test
    fun `the visitor-permit saga is wired and collected as an outbox handler`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBeansOfType(OutboxHandler::class.java).values)
                .anyMatch { it is VisitorPermitSaga }
        }
    }
}
