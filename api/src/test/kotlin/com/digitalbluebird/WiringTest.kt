package com.digitalbluebird

import com.digitalbluebird.bookings.config.BookingsConfiguration
import com.digitalbluebird.bookings.domain.port.inbound.CancelBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.ConfirmBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.FindBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.ViewHideAvailabilityUseCase
import com.digitalbluebird.identity.config.IdentityConfiguration
import com.digitalbluebird.identity.domain.port.inbound.CreateUserUseCase
import com.digitalbluebird.identity.domain.port.inbound.FindUserUseCase
import com.digitalbluebird.observations.config.ObservationsConfiguration
import com.digitalbluebird.observations.domain.port.inbound.FindSightingUseCase
import com.digitalbluebird.observations.domain.port.inbound.RecordSightingUseCase
import com.digitalbluebird.shared.infra.SharedInfraConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier
import javax.sql.DataSource

/**
 * Guards the app's bean wiring: every inbound use-case port must resolve to exactly one bean. If a
 * service is ever exposed as both a concrete-typed and an interface-typed bean, the port injection in
 * its controller becomes ambiguous (NoUniqueBeanDefinitionException at context startup) — this test
 * fails first. Runs with stub infrastructure (no database), so it needs no Docker. (Species is
 * covered by SpeciesWiringTest in that module, where the Elasticsearch client is on the classpath.)
 */
class WiringTest {

    private val ports = listOf(
        CreateUserUseCase::class.java,
        FindUserUseCase::class.java,
        RecordSightingUseCase::class.java,
        FindSightingUseCase::class.java,
        RequestBookingUseCase::class.java,
        ConfirmBookingUseCase::class.java,
        CancelBookingUseCase::class.java,
        FindBookingUseCase::class.java,
        ViewHideAvailabilityUseCase::class.java,
    )

    private val runner = ApplicationContextRunner()
        .withBean(DataSource::class.java, Supplier { mock(DataSource::class.java) })
        .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
        .withUserConfiguration(
            SharedInfraConfiguration::class.java,
            IdentityConfiguration::class.java,
            ObservationsConfiguration::class.java,
            BookingsConfiguration::class.java,
        )

    @Test
    fun `every inbound use-case port resolves to exactly one bean`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            ports.forEach { port ->
                assertThat(context.getBeanNamesForType(port))
                    .describedAs("beans for port %s", port.simpleName)
                    .hasSize(1)
            }
        }
    }
}
