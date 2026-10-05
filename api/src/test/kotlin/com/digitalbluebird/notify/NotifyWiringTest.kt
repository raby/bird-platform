package com.digitalbluebird.notify

import com.digitalbluebird.notifications.config.NotificationsConfiguration
import com.digitalbluebird.notifications.domain.port.inbound.NotifyUseCase
import com.digitalbluebird.notifications.domain.port.inbound.ViewNotificationsUseCase
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
 * Guards the notifications wiring: with the shared infra and notifications configurations present, the
 * booking→notification bridge resolves the NotifyUseCase port and is collected as an [OutboxHandler],
 * so the shared relay drives it alongside the projector and the saga; and each notifications inbound
 * port resolves to exactly one bean (the ambiguity guard). No Docker — the DataSource is mocked and
 * never touched at wiring time.
 */
class NotifyWiringTest {

    private val runner = ApplicationContextRunner()
        .withBean(DataSource::class.java, Supplier { mockk<DataSource>(relaxed = true) })
        .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
        .withUserConfiguration(
            SharedInfraConfiguration::class.java,
            NotificationsConfiguration::class.java,
            NotifyConfiguration::class.java,
        )

    @Test
    fun `the booking notifier is wired and collected as an outbox handler`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBeansOfType(OutboxHandler::class.java).values)
                .anyMatch { it is BookingNotifier }
        }
    }

    @Test
    fun `each notifications inbound port resolves to exactly one bean`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBeanNamesForType(NotifyUseCase::class.java)).hasSize(1)
            assertThat(context.getBeanNamesForType(ViewNotificationsUseCase::class.java)).hasSize(1)
        }
    }
}
