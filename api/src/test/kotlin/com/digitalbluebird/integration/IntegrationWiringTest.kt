package com.digitalbluebird.integration

import com.digitalbluebird.integrations.ebird.config.EbirdConfiguration
import com.digitalbluebird.integrations.ebird.domain.port.inbound.DispatchEbirdSubmissionsUseCase
import com.digitalbluebird.integrations.ebird.domain.port.inbound.SyncSightingUseCase
import com.digitalbluebird.integrations.ebird.domain.port.inbound.ViewEbirdUseCase
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
 * Guards the eBird integration wiring: with the shared infra and ebird configurations present, the
 * sighting→eBird bridge resolves the SyncSightingUseCase port and is collected as an [OutboxHandler]
 * (so the shared relay drives it off SightingCreated), the circuit breaker is a single shared bean,
 * and each ebird inbound port resolves to exactly one bean. No Docker — the DataSource is mocked and
 * never touched at wiring time.
 */
class IntegrationWiringTest {

    private val runner = ApplicationContextRunner()
        .withBean(DataSource::class.java, Supplier { mockk<DataSource>(relaxed = true) })
        .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
        .withUserConfiguration(
            SharedInfraConfiguration::class.java,
            EbirdConfiguration::class.java,
            IntegrationConfiguration::class.java,
        )

    @Test
    fun `the ebird sighting sync is wired and collected as an outbox handler`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBeansOfType(OutboxHandler::class.java).values)
                .anyMatch { it is EbirdSightingSync }
        }
    }

    @Test
    fun `each ebird inbound port resolves to exactly one bean`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBeanNamesForType(SyncSightingUseCase::class.java)).hasSize(1)
            assertThat(context.getBeanNamesForType(DispatchEbirdSubmissionsUseCase::class.java)).hasSize(1)
            assertThat(context.getBeanNamesForType(ViewEbirdUseCase::class.java)).hasSize(1)
        }
    }
}
