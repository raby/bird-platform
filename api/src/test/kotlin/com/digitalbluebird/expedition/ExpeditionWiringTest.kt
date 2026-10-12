package com.digitalbluebird.expedition

import com.digitalbluebird.bookings.config.BookingsConfiguration
import com.digitalbluebird.expeditions.config.ExpeditionsConfiguration
import com.digitalbluebird.expeditions.domain.port.inbound.HandleBookingCancelledUseCase
import com.digitalbluebird.expeditions.domain.port.inbound.PlanExpeditionUseCase
import com.digitalbluebird.expeditions.domain.port.inbound.ViewExpeditionUseCase
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
 * Guards the expedition process-manager wiring: with shared infra, bookings and expeditions present, the
 * HideBookingGateway adapter resolves the bookings ports, the expedition service resolves it, and the
 * BookingCancelled bridge is collected as an [OutboxHandler] so the shared relay drives it; and each
 * expedition inbound port resolves to exactly one bean. No Docker — the DataSource is mocked.
 */
class ExpeditionWiringTest {

    private val runner = ApplicationContextRunner()
        .withBean(DataSource::class.java, Supplier { mockk<DataSource>(relaxed = true) })
        .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
        .withUserConfiguration(
            SharedInfraConfiguration::class.java,
            BookingsConfiguration::class.java,
            ExpeditionsConfiguration::class.java,
            ExpeditionWiring::class.java,
        )

    @Test
    fun `the expedition process manager is wired and collected as an outbox handler`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBeansOfType(OutboxHandler::class.java).values)
                .anyMatch { it is ExpeditionProcessManager }
        }
    }

    @Test
    fun `each expedition inbound port resolves to exactly one bean`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBeanNamesForType(PlanExpeditionUseCase::class.java)).hasSize(1)
            assertThat(context.getBeanNamesForType(ViewExpeditionUseCase::class.java)).hasSize(1)
            assertThat(context.getBeanNamesForType(HandleBookingCancelledUseCase::class.java)).hasSize(1)
        }
    }
}
