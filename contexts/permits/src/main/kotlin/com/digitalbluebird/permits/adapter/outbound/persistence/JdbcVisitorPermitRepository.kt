package com.digitalbluebird.permits.adapter.outbound.persistence

import com.digitalbluebird.permits.domain.port.outbound.ReserveOutcome
import com.digitalbluebird.permits.domain.port.outbound.VisitorPermitRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class JdbcVisitorPermitRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : VisitorPermitRepository {

    override fun isIssued(bookingId: UUID): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM permits.permit_issuance WHERE booking_id = :booking_id)",
            MapSqlParameterSource("booking_id", bookingId),
            Boolean::class.java,
        ) ?: false

    override fun reserve(hideId: UUID, day: LocalDate, partySize: Int): ReserveOutcome {
        val params = MapSqlParameterSource()
            .addValue("hide_id", hideId)
            .addValue("day", day)
            .addValue("party_size", partySize)

        // Atomic conditional increment: a single row-locked UPDATE, so concurrent reserves can never
        // push the day's issued count past the cap.
        val incremented = jdbc.update(
            """
            UPDATE permits.visitor_permits
               SET issued = issued + :party_size
             WHERE hide_id = :hide_id
               AND day = :day
               AND issued + :party_size <= capacity
            """.trimIndent(),
            params,
        )
        if (incremented == 1) return ReserveOutcome.RESERVED

        // The increment did nothing: either no cap row exists (unlimited) or the cap is already full.
        val capExists = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM permits.visitor_permits WHERE hide_id = :hide_id AND day = :day)",
            params,
            Boolean::class.java,
        ) ?: false
        return if (capExists) ReserveOutcome.EXHAUSTED else ReserveOutcome.NO_CAP
    }

    override fun recordIssuance(bookingId: UUID, hideId: UUID, day: LocalDate, partySize: Int, at: Instant) {
        jdbc.update(
            """
            INSERT INTO permits.permit_issuance (booking_id, hide_id, day, party_size, issued_at)
            VALUES (:booking_id, :hide_id, :day, :party_size, :issued_at)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("booking_id", bookingId)
                .addValue("hide_id", hideId)
                .addValue("day", day)
                .addValue("party_size", partySize)
                .addValue("issued_at", Timestamp.from(at)),
        )
    }
}
