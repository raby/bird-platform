package com.digitalbluebird.bookings.adapter.outbound.persistence

import com.digitalbluebird.bookings.domain.Booking
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.BookingStatus
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.PartySize
import com.digitalbluebird.bookings.domain.port.outbound.BookingRepository
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.util.UUID

class JdbcBookingRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : BookingRepository {

    override fun insert(booking: Booking): Booking {
        val params = MapSqlParameterSource()
            .addValue("id", booking.id.value)
            .addValue("hide_id", booking.hideId.value)
            .addValue("observer_id", booking.observerId.value)
            .addValue("slot_start", Timestamp.from(booking.slot.start))
            .addValue("slot_end", Timestamp.from(booking.slot.endExclusive))
            .addValue("party_size", booking.partySize.value)
            .addValue("status", booking.status.name)
            .addValue("version", booking.version)
            .addValue("created_at", Timestamp.from(booking.createdAt))
            .addValue("updated_at", Timestamp.from(booking.updatedAt))

        try {
            jdbc.update(
                """
                INSERT INTO bookings.bookings
                    (id, hide_id, observer_id, slot_start, slot_end, party_size,
                     status, version, created_at, updated_at)
                VALUES
                    (:id, :hide_id, :observer_id, :slot_start, :slot_end, :party_size,
                     :status, :version, :created_at, :updated_at)
                """.trimIndent(),
                params,
            )
        } catch (e: DataIntegrityViolationException) {
            // The bookings_no_overlapping_active_slot exclusion constraint (V303) rejected an
            // overlapping active booking — the storage-layer guard firing under a race. Surface it as
            // a semantic signal; any other integrity violation is a real fault and propagates as-is.
            if (isSlotExclusionViolation(e)) throw OverlappingSlotException(booking.hideId, e)
            throw e
        }
        return booking
    }

    // Postgres raises SQLSTATE 23P01 (exclusion_violation) for the slot constraint. Walk the cause
    // chain rather than matching on the message text, so detection is driver- and locale-independent.
    private fun isSlotExclusionViolation(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is SQLException && cause.sqlState == EXCLUSION_VIOLATION_SQLSTATE) return true
            cause = cause.cause
        }
        return false
    }

    override fun findById(id: BookingId): Booking? {
        val params = MapSqlParameterSource("id", id.value)
        return jdbc.query(
            """
            SELECT id, hide_id, observer_id, slot_start, slot_end, party_size,
                   status, version, created_at, updated_at
            FROM bookings.bookings
            WHERE id = :id
            """.trimIndent(),
            params,
            ::mapRow,
        ).firstOrNull()
    }

    override fun updateIfVersionMatches(booking: Booking, expectedVersion: Long): Booking? {
        val nextVersion = expectedVersion + 1
        val params = MapSqlParameterSource()
            .addValue("id", booking.id.value)
            .addValue("status", booking.status.name)
            .addValue("updated_at", Timestamp.from(booking.updatedAt))
            .addValue("next_version", nextVersion)
            .addValue("expected_version", expectedVersion)

        val rows = jdbc.update(
            """
            UPDATE bookings.bookings
               SET status      = :status,
                   updated_at  = :updated_at,
                   version     = :next_version
             WHERE id = :id
               AND version = :expected_version
            """.trimIndent(),
            params,
        )
        return if (rows == 1) booking.copy(version = nextVersion) else null
    }

    override fun hasOverlappingActiveBooking(hideId: HideId, slot: InstantRange): Boolean {
        val params = MapSqlParameterSource()
            .addValue("hide_id", hideId.value)
            .addValue("slot_start", Timestamp.from(slot.start))
            .addValue("slot_end", Timestamp.from(slot.endExclusive))
        val count = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM bookings.bookings
            WHERE hide_id = :hide_id
              AND status <> 'CANCELLED'
              AND slot_start < :slot_end
              AND slot_end   > :slot_start
            """.trimIndent(),
            params,
            Int::class.java,
        ) ?: 0
        return count > 0
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): Booking = Booking(
        id = BookingId(rs.getObject("id", UUID::class.java)),
        hideId = HideId(rs.getObject("hide_id", UUID::class.java)),
        observerId = ObserverId(rs.getObject("observer_id", UUID::class.java)),
        slot = InstantRange(
            start = rs.getTimestamp("slot_start").toInstant(),
            endExclusive = rs.getTimestamp("slot_end").toInstant(),
        ),
        partySize = PartySize(rs.getInt("party_size")),
        status = BookingStatus.valueOf(rs.getString("status")),
        version = rs.getLong("version"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    private companion object {
        const val EXCLUSION_VIOLATION_SQLSTATE = "23P01"
    }
}
