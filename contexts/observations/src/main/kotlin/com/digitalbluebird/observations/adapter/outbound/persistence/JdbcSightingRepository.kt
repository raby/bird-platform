package com.digitalbluebird.observations.adapter.outbound.persistence

import com.digitalbluebird.observations.domain.Count
import com.digitalbluebird.observations.domain.Sighting
import com.digitalbluebird.observations.domain.SightingId
import com.digitalbluebird.observations.domain.port.outbound.SightingRepository
import com.digitalbluebird.shared.domain.Location
import com.digitalbluebird.shared.domain.ObserverId
import com.digitalbluebird.shared.domain.SpeciesId
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class JdbcSightingRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : SightingRepository {

    override fun save(sighting: Sighting): Sighting {
        val params = MapSqlParameterSource()
            .addValue("id", sighting.id.value)
            .addValue("observer_id", sighting.observerId.value)
            .addValue("species_id", sighting.speciesId.value)
            .addValue("latitude", sighting.location.latitude.value)
            .addValue("longitude", sighting.location.longitude.value)
            .addValue("observed_at", Timestamp.from(sighting.observedAt))
            .addValue("count", sighting.count.value)
            .addValue("notes", sighting.notes)
            .addValue("created_at", Timestamp.from(sighting.createdAt))

        jdbc.update(
            """
            INSERT INTO observations.sightings
                (id, observer_id, species_id, latitude, longitude, observed_at, count, notes, created_at)
            VALUES
                (:id, :observer_id, :species_id, :latitude, :longitude, :observed_at, :count, :notes, :created_at)
            """.trimIndent(),
            params,
        )
        return sighting
    }

    override fun findById(id: SightingId): Sighting? {
        val params = MapSqlParameterSource("id", id.value)
        return jdbc.query(
            """
            SELECT id, observer_id, species_id, latitude, longitude,
                   observed_at, count, notes, created_at
            FROM observations.sightings
            WHERE id = :id
            """.trimIndent(),
            params,
            ::mapRow,
        ).firstOrNull()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): Sighting = Sighting(
        id = SightingId(rs.getObject("id", UUID::class.java)),
        observerId = ObserverId(rs.getObject("observer_id", UUID::class.java)),
        speciesId = SpeciesId(rs.getString("species_id")),
        location = Location.of(rs.getDouble("latitude"), rs.getDouble("longitude")),
        observedAt = rs.getTimestamp("observed_at").toInstant(),
        count = Count(rs.getInt("count")),
        notes = rs.getString("notes"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )
}
