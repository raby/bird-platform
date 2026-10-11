package com.digitalbluebird.integrations.ebird.adapter.outbound.persistence

import com.digitalbluebird.integrations.ebird.domain.EbirdSubmission
import com.digitalbluebird.integrations.ebird.domain.SubmissionStatus
import com.digitalbluebird.integrations.ebird.domain.port.outbound.EbirdSubmissionRepository
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class JdbcEbirdSubmissionRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : EbirdSubmissionRepository {

    override fun enqueue(submission: EbirdSubmission): Boolean {
        // Idempotent on sighting_id: a re-delivered SightingCreated hits the PK and is a no-op.
        val inserted = jdbc.update(
            """
            INSERT INTO ebird.submission
                (sighting_id, species_id, latitude, longitude, observed_at, observed_count,
                 status, attempts, last_error, created_at, updated_at, submitted_at)
            VALUES (:sighting_id, :species_id, :latitude, :longitude, :observed_at, :observed_count,
                    :status, :attempts, :last_error, :created_at, :updated_at, :submitted_at)
            ON CONFLICT (sighting_id) DO NOTHING
            """.trimIndent(),
            submission.params(),
        )
        return inserted == 1
    }

    override fun findPending(limit: Int): List<EbirdSubmission> =
        jdbc.query(
            """
            $SELECT_COLUMNS
            FROM ebird.submission
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            """.trimIndent(),
            MapSqlParameterSource("limit", limit),
            rowMapper,
        )

    override fun update(submission: EbirdSubmission) {
        jdbc.update(
            """
            UPDATE ebird.submission
               SET status = :status, attempts = :attempts, last_error = :last_error,
                   updated_at = :updated_at, submitted_at = :submitted_at
             WHERE sighting_id = :sighting_id
            """.trimIndent(),
            submission.params(),
        )
    }

    override fun recent(limit: Int): List<EbirdSubmission> =
        jdbc.query(
            """
            $SELECT_COLUMNS
            FROM ebird.submission
            ORDER BY created_at DESC
            LIMIT :limit
            """.trimIndent(),
            MapSqlParameterSource("limit", limit),
            rowMapper,
        )

    private fun EbirdSubmission.params() = MapSqlParameterSource()
        .addValue("sighting_id", UUID.fromString(sightingId))
        .addValue("species_id", speciesId)
        .addValue("latitude", latitude)
        .addValue("longitude", longitude)
        .addValue("observed_at", Timestamp.from(observedAt))
        .addValue("observed_count", count)
        .addValue("status", status.name)
        .addValue("attempts", attempts)
        .addValue("last_error", lastError)
        .addValue("created_at", Timestamp.from(createdAt))
        .addValue("updated_at", Timestamp.from(updatedAt))
        .addValue("submitted_at", submittedAt?.let(Timestamp::from))

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        EbirdSubmission(
            sightingId = rs.getObject("sighting_id", UUID::class.java).toString(),
            speciesId = rs.getString("species_id"),
            latitude = rs.getDouble("latitude"),
            longitude = rs.getDouble("longitude"),
            observedAt = rs.getTimestamp("observed_at").toInstant(),
            count = rs.getInt("observed_count"),
            status = SubmissionStatus.valueOf(rs.getString("status")),
            attempts = rs.getInt("attempts"),
            lastError = rs.getString("last_error"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            submittedAt = rs.getTimestamp("submitted_at")?.toInstant(),
        )
    }

    private companion object {
        const val SELECT_COLUMNS =
            "SELECT sighting_id, species_id, latitude, longitude, observed_at, observed_count, " +
                "status, attempts, last_error, created_at, updated_at, submitted_at"
    }
}
