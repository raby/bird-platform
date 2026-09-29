package com.digitalbluebird.bookings.adapter.outbound.persistence

import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.Hide
import com.digitalbluebird.bookings.domain.HideAvailability
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.port.outbound.ConfirmedStay
import com.digitalbluebird.bookings.domain.port.outbound.HideAvailabilityReadModel
import com.digitalbluebird.shared.domain.InstantRange
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class JdbcHideAvailabilityReadModel(
    private val jdbc: NamedParameterJdbcTemplate,
) : HideAvailabilityReadModel {

    override fun applyConfirmed(stay: ConfirmedStay) {
        val params = MapSqlParameterSource()
            .addValue("booking_id", stay.bookingId.value)
            .addValue("hide_id", stay.hideId.value)
            .addValue("observer_id", stay.observerId.value)
            .addValue("slot_start", Timestamp.from(stay.slot.start))
            .addValue("slot_end", Timestamp.from(stay.slot.endExclusive))
            .addValue("party_size", stay.partySize.value)
            .addValue("confirmed_at", Timestamp.from(stay.confirmedAt))

        // Upsert keyed by booking_id makes re-delivery idempotent.
        jdbc.update(
            """
            INSERT INTO bookings.hide_availability
                (booking_id, hide_id, observer_id, slot_start, slot_end, party_size, confirmed_at)
            VALUES
                (:booking_id, :hide_id, :observer_id, :slot_start, :slot_end, :party_size, :confirmed_at)
            ON CONFLICT (booking_id) DO UPDATE SET
                hide_id      = EXCLUDED.hide_id,
                observer_id  = EXCLUDED.observer_id,
                slot_start   = EXCLUDED.slot_start,
                slot_end     = EXCLUDED.slot_end,
                party_size   = EXCLUDED.party_size,
                confirmed_at = EXCLUDED.confirmed_at
            """.trimIndent(),
            params,
        )
    }

    override fun removeByBooking(bookingId: BookingId) {
        jdbc.update(
            "DELETE FROM bookings.hide_availability WHERE booking_id = :booking_id",
            MapSqlParameterSource("booking_id", bookingId.value),
        )
    }

    override fun listHides(): List<Hide> =
        jdbc.query(
            "SELECT id, name, reserve, capacity FROM bookings.hides ORDER BY name",
            MapSqlParameterSource(),
            ::mapHide,
        )

    override fun availabilityFor(hideId: HideId, slot: InstantRange): HideAvailability? {
        val hide = jdbc.query(
            "SELECT id, name, reserve, capacity FROM bookings.hides WHERE id = :id",
            MapSqlParameterSource("id", hideId.value),
            ::mapHide,
        ).firstOrNull() ?: return null

        val params = MapSqlParameterSource()
            .addValue("hide_id", hideId.value)
            .addValue("slot_start", Timestamp.from(slot.start))
            .addValue("slot_end", Timestamp.from(slot.endExclusive))
        val occupied = jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(party_size), 0)::int
            FROM bookings.hide_availability
            WHERE hide_id = :hide_id
              AND slot_start < :slot_end
              AND slot_end   > :slot_start
            """.trimIndent(),
            params,
            Int::class.java,
        ) ?: 0

        return HideAvailability(hide = hide, slot = slot, occupied = occupied)
    }

    private fun mapHide(rs: ResultSet, rowNum: Int): Hide = Hide(
        id = HideId(rs.getObject("id", UUID::class.java)),
        name = rs.getString("name"),
        reserve = rs.getString("reserve"),
        capacity = rs.getInt("capacity"),
    )
}
