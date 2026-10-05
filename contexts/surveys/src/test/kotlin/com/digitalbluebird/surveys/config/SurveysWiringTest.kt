package com.digitalbluebird.surveys.config

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isNull
import com.digitalbluebird.shared.infra.SharedInfraConfiguration
import com.digitalbluebird.surveys.domain.port.inbound.ClaimPlotUseCase
import com.digitalbluebird.surveys.domain.port.inbound.ReleasePlotUseCase
import com.digitalbluebird.surveys.domain.port.inbound.ViewPlotUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier
import javax.sql.DataSource

/**
 * Guards the surveys wiring: each inbound port resolves to exactly one bean, so SurveyPlotController's
 * port injection stays unambiguous. No Docker needed — the DataSource is mocked and untouched at
 * wiring time.
 */
class SurveysWiringTest {

    private val runner = ApplicationContextRunner()
        .withBean(DataSource::class.java, Supplier { mockk<DataSource>(relaxed = true) })
        .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
        .withUserConfiguration(SharedInfraConfiguration::class.java, SurveysConfiguration::class.java)

    @Test
    fun `each survey inbound port resolves to exactly one bean`() {
        runner.run { context ->
            assertThat(context.startupFailure).isNull()
            assertThat(context.getBeanNamesForType(ClaimPlotUseCase::class.java).toList()).hasSize(1)
            assertThat(context.getBeanNamesForType(ReleasePlotUseCase::class.java).toList()).hasSize(1)
            assertThat(context.getBeanNamesForType(ViewPlotUseCase::class.java).toList()).hasSize(1)
        }
    }
}
