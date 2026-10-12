package com.digitalbluebird.expeditions.adapter.outbound.persistence

import com.digitalbluebird.expeditions.domain.Expedition
import com.digitalbluebird.expeditions.domain.ExpeditionId
import com.digitalbluebird.expeditions.domain.ExpeditionLeg
import com.digitalbluebird.expeditions.domain.ExpeditionStatus
import com.digitalbluebird.expeditions.domain.LegStatus
import com.digitalbluebird.expeditions.domain.port.outbound.ExpeditionRepository
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class JdbcExpeditionRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : ExpeditionRepository {

    override fun save(expedition: Expedition) {
        jdbc.update(
            """
            INSERT INTO expeditions.expedition (id, leader_id, party_size, status, created_at, updated_at)
            VALUES (:id, :leader_id, :party_size, :status, :created_at, :updated_at)
            ON CONFLICT (id) DO UPDATE SET status = :status, updated_at = :updated_at
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", expedition.id.value)
                .addValue("leader_id", expedition.leaderId)
                .addValue("party_size", expedition.partySize)
                .addValue("status", expedition.status.name)
                .addValue("created_at", Timestamp.from(expedition.createdAt))
                .addValue("updated_at", Timestamp.from(expedition.updatedAt)),
        )

        // Legs are fixed at planning time; later saves update their status/booking in place, so an upsert
        // keyed on (expedition_id, seq) never leaves a leg missing even if a save is interrupted.
        expedition.legs.forEachIndexed { seq, leg ->
            jdbc.update(
                """
                INSERT INTO expeditions.expedition_leg
                    (expedition_id, seq, hide_id, slot_start, slot_end, booking_id, status)
                VALUES (:expedition_id, :seq, :hide_id, :slot_start, :slot_end, :booking_id, :status)
                ON CONFLICT (expedition_id, seq) DO UPDATE SET
                    hide_id = :hide_id, slot_start = :slot_start, slot_end = :slot_end,
                    booking_id = :booking_id, status = :status
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("expedition_id", expedition.id.value)
                    .addValue("seq", seq)
                    .addValue("hide_id", leg.hideId)
                    .addValue("slot_start", Timestamp.from(leg.slotStart))
                    .addValue("slot_end", Timestamp.from(leg.slotEnd))
                    .addValue("booking_id", leg.bookingId?.let(UUID::fromString))
                    .addValue("status", leg.status.name),
            )
        }
    }

    override fun findById(id: ExpeditionId): Expedition? {
        val row = jdbc.query(
            "SELECT id, leader_id, party_size, status, created_at, updated_at FROM expeditions.expedition WHERE id = :id",
            MapSqlParameterSource("id", id.value),
            expeditionRowMapper,
        ).firstOrNull() ?: return null

        val legs = jdbc.query(
            """
            SELECT hide_id, slot_start, slot_end, booking_id, status
            FROM expeditions.expedition_leg WHERE expedition_id = :id ORDER BY seq
            """.trimIndent(),
            MapSqlParameterSource("id", id.value),
            legRowMapper,
        )
        return row.copy(legs = legs)
    }

    override fun findByLegBookingId(bookingId: String): Expedition? {
        val expeditionId = jdbc.query(
            "SELECT expedition_id FROM expeditions.expedition_leg WHERE booking_id = :booking_id",
            MapSqlParameterSource("booking_id", UUID.fromString(bookingId)),
            { rs, _ -> ExpeditionId(rs.getObject("expedition_id", UUID::class.java)) },
        ).firstOrNull() ?: return null
        return findById(expeditionId)
    }

    // Built without legs; findById fills them in.
    private val expeditionRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Expedition(
            id = ExpeditionId(rs.getObject("id", UUID::class.java)),
            leaderId = rs.getString("leader_id"),
            partySize = rs.getInt("party_size"),
            status = ExpeditionStatus.valueOf(rs.getString("status")),
            legs = emptyList(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
    }

    private val legRowMapper = RowMapper { rs: ResultSet, _: Int ->
        ExpeditionLeg(
            hideId = rs.getString("hide_id"),
            slotStart = rs.getTimestamp("slot_start").toInstant(),
            slotEnd = rs.getTimestamp("slot_end").toInstant(),
            bookingId = rs.getObject("booking_id", UUID::class.java)?.toString(),
            status = LegStatus.valueOf(rs.getString("status")),
        )
    }
}
