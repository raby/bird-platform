package com.digitalbluebird.shared.infra.outbox

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

class JdbcOutboxRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : OutboxRepository {

    override fun save(entry: OutboxEntry) {
        val params = MapSqlParameterSource()
            .addValue("id", entry.id)
            .addValue("aggregate_type", entry.aggregateType)
            .addValue("aggregate_id", entry.aggregateId)
            .addValue("event_type", entry.eventType)
            .addValue("payload", entry.payload)
            .addValue("occurred_at", Timestamp.from(entry.occurredAt))
            .addValue("published_at", entry.publishedAt?.let(Timestamp::from), Types.TIMESTAMP_WITH_TIMEZONE)

        jdbc.update(
            """
            INSERT INTO shared_infra.outbox
                (id, aggregate_type, aggregate_id, event_type, payload, occurred_at, published_at)
            VALUES
                (:id, :aggregate_type, :aggregate_id, :event_type, CAST(:payload AS JSONB), :occurred_at, :published_at)
            """.trimIndent(),
            params,
        )
    }

    override fun findUnpublished(limit: Int): List<OutboxEntry> {
        val params = MapSqlParameterSource("limit", limit)
        return jdbc.query(
            """
            SELECT id, aggregate_type, aggregate_id, event_type, payload, occurred_at, published_at
            FROM shared_infra.outbox
            WHERE published_at IS NULL
            ORDER BY occurred_at
            LIMIT :limit
            """.trimIndent(),
            params,
            ::mapRow,
        )
    }

    override fun markPublished(id: UUID, at: Instant) {
        val params = MapSqlParameterSource()
            .addValue("id", id)
            .addValue("published_at", Timestamp.from(at))
        jdbc.update(
            "UPDATE shared_infra.outbox SET published_at = :published_at WHERE id = :id",
            params,
        )
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): OutboxEntry = OutboxEntry(
        id = rs.getObject("id", UUID::class.java),
        aggregateType = rs.getString("aggregate_type"),
        aggregateId = rs.getString("aggregate_id"),
        eventType = rs.getString("event_type"),
        payload = rs.getString("payload"),
        occurredAt = rs.getTimestamp("occurred_at").toInstant(),
        publishedAt = rs.getTimestamp("published_at")?.toInstant(),
    )
}
