package com.digitalbluebird.surveys.config

import com.digitalbluebird.surveys.adapter.outbound.persistence.JdbcSurveyPlotEventStore
import com.digitalbluebird.surveys.application.SurveyPlotService
import com.digitalbluebird.surveys.domain.port.outbound.SurveyPlotEventStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock

@Configuration
class SurveysConfiguration {

    @Bean
    fun surveyPlotEventStore(jdbc: NamedParameterJdbcTemplate, objectMapper: ObjectMapper): SurveyPlotEventStore =
        JdbcSurveyPlotEventStore(jdbc, objectMapper)

    // Single concrete bean behind the Claim/Release/ViewPlot ports (mirroring the other contexts), so
    // SurveyPlotController's port injection stays unambiguous.
    @Bean
    fun surveyPlotService(eventStore: SurveyPlotEventStore, clock: Clock): SurveyPlotService =
        SurveyPlotService(eventStore, clock)
}
