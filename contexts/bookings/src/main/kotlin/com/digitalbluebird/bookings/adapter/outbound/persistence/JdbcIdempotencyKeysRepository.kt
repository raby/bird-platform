package com.digitalbluebird.bookings.adapter.outbound.persistence

import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.IdempotencyKey
import com.digitalbluebird.bookings.domain.port.outbound.IdempotencyKeysRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.UUID

class JdbcIdempotencyKeysRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : IdempotencyKeysRepository {

    override fun register(key: IdempotencyKey, bookingId: BookingId): Boolean {
        val params = MapSqlParameterSource()
            .addValue("key", key.value)
            .addValue("booking_id", bookingId.value)
        return try {
            jdbc.update(
                """
                INSERT INTO bookings.idempotency_keys (key, booking_id)
                VALUES (:key, :booking_id)
                """.trimIndent(),
                params,
            )
            true
        } catch (_: DuplicateKeyException) {
            false
        }
    }

    override fun findBookingId(key: IdempotencyKey): BookingId? {
        val params = MapSqlParameterSource("key", key.value)
        return jdbc.query(
            """
            SELECT booking_id FROM bookings.idempotency_keys WHERE key = :key
            """.trimIndent(),
            params,
        ) { rs, _ -> BookingId(rs.getObject("booking_id", UUID::class.java)) }
            .firstOrNull()
    }
}
