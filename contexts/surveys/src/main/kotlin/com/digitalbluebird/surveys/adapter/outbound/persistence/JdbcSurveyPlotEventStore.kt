package com.digitalbluebird.surveys.adapter.outbound.persistence

import com.digitalbluebird.surveys.domain.SurveyPlotId
import com.digitalbluebird.surveys.domain.event.PlotClaimed
import com.digitalbluebird.surveys.domain.event.PlotReleased
import com.digitalbluebird.surveys.domain.event.SurveyPlotEvent
import com.digitalbluebird.surveys.domain.port.outbound.SurveyPlotEventStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp

class JdbcSurveyPlotEventStore(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) : SurveyPlotEventStore {

    override fun load(plotId: SurveyPlotId): List<SurveyPlotEvent> =
        jdbc.query(
            """
            SELECT event_type, payload
            FROM surveys.survey_plot_events
            WHERE plot_id = :plot_id
            ORDER BY sequence
            """.trimIndent(),
            MapSqlParameterSource("plot_id", plotId.value),
            ::mapRow,
        )

    override fun append(plotId: SurveyPlotId, expectedVersion: Int, event: SurveyPlotEvent): Boolean {
        val params = MapSqlParameterSource()
            .addValue("plot_id", plotId.value)
            .addValue("sequence", expectedVersion)
            .addValue("event_type", eventType(event))
            .addValue("payload", objectMapper.writeValueAsString(event))
            .addValue("occurred_at", Timestamp.from(event.occurredAt))
        return try {
            jdbc.update(
                """
                INSERT INTO surveys.survey_plot_events (plot_id, sequence, event_type, payload, occurred_at)
                VALUES (:plot_id, :sequence, :event_type, CAST(:payload AS JSONB), :occurred_at)
                """.trimIndent(),
                params,
            )
            true
        } catch (_: DuplicateKeyException) {
            // The (plot_id, sequence) primary key rejected the append: another writer already took this
            // stream position. Optimistic concurrency lost — the caller reports ConcurrencyConflict.
            false
        }
    }

    private fun eventType(event: SurveyPlotEvent): String = when (event) {
        is PlotClaimed -> PlotClaimed.EVENT_TYPE
        is PlotReleased -> PlotReleased.EVENT_TYPE
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): SurveyPlotEvent {
        val payload = rs.getString("payload")
        return when (val type = rs.getString("event_type")) {
            PlotClaimed.EVENT_TYPE -> objectMapper.readValue(payload, PlotClaimed::class.java)
            PlotReleased.EVENT_TYPE -> objectMapper.readValue(payload, PlotReleased::class.java)
            else -> error("unknown survey plot event type: $type")
        }
    }
}
