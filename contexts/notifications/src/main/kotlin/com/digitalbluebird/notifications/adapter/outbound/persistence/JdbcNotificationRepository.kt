package com.digitalbluebird.notifications.adapter.outbound.persistence

import com.digitalbluebird.notifications.domain.Notification
import com.digitalbluebird.notifications.domain.NotificationId
import com.digitalbluebird.notifications.domain.NotificationStatus
import com.digitalbluebird.notifications.domain.port.outbound.NotificationRepository
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class JdbcNotificationRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : NotificationRepository {

    override fun enqueue(notification: Notification): Boolean {
        // The inbox dedup: a re-delivered source event hits the UNIQUE(source_event_id) and is a no-op,
        // so one event yields at most one notification. `update` returns the rows inserted (0 or 1).
        val inserted = jdbc.update(
            """
            INSERT INTO notifications.notification
                (id, kind, recipient, subject, body, source_event_id, status, created_at)
            VALUES (:id, :kind, :recipient, :subject, :body, :source_event_id, :status, :created_at)
            ON CONFLICT (source_event_id) DO NOTHING
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", notification.id.value)
                .addValue("kind", notification.kind)
                .addValue("recipient", notification.recipient)
                .addValue("subject", notification.subject)
                .addValue("body", notification.body)
                .addValue("source_event_id", notification.sourceEventId)
                .addValue("status", notification.status.name)
                .addValue("created_at", Timestamp.from(notification.createdAt)),
        )
        return inserted == 1
    }

    override fun findPending(limit: Int): List<Notification> =
        jdbc.query(
            """
            SELECT id, kind, recipient, subject, body, source_event_id, status, created_at, sent_at
            FROM notifications.notification
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            """.trimIndent(),
            MapSqlParameterSource("limit", limit),
            rowMapper,
        )

    override fun markSent(id: NotificationId, at: Instant) {
        // Guard on status so a re-mark (e.g. an at-least-once retry after the send succeeded but the
        // mark was lost) is a no-op rather than overwriting sent_at — matching Notification.markSent.
        jdbc.update(
            "UPDATE notifications.notification SET status = 'SENT', sent_at = :sent_at WHERE id = :id AND status = 'PENDING'",
            MapSqlParameterSource()
                .addValue("sent_at", Timestamp.from(at))
                .addValue("id", id.value),
        )
    }

    override fun recent(limit: Int): List<Notification> =
        jdbc.query(
            """
            SELECT id, kind, recipient, subject, body, source_event_id, status, created_at, sent_at
            FROM notifications.notification
            ORDER BY created_at DESC
            LIMIT :limit
            """.trimIndent(),
            MapSqlParameterSource("limit", limit),
            rowMapper,
        )

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        Notification(
            id = NotificationId(rs.getObject("id", UUID::class.java)),
            kind = rs.getString("kind"),
            recipient = rs.getString("recipient"),
            subject = rs.getString("subject"),
            body = rs.getString("body"),
            sourceEventId = rs.getObject("source_event_id", UUID::class.java),
            status = NotificationStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            sentAt = rs.getTimestamp("sent_at")?.toInstant(),
        )
    }
}
