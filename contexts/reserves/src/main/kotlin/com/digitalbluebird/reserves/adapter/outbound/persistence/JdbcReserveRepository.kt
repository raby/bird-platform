package com.digitalbluebird.reserves.adapter.outbound.persistence

import com.digitalbluebird.reserves.domain.NearbyReserve
import com.digitalbluebird.reserves.domain.Reserve
import com.digitalbluebird.reserves.domain.ReserveId
import com.digitalbluebird.reserves.domain.port.outbound.ReserveRepository
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.util.UUID

/**
 * PostGIS-backed reserve catalogue. [findNearby] selects reserves within the radius with `ST_DWithin`
 * on a `geography(Point)` column and orders them nearest-first with the `<->` KNN operator — both
 * served by the GIST index on `location` — and reports each distance with `ST_Distance`. Distances are
 * metres in PostGIS geography; the query converts to kilometres.
 */
class JdbcReserveRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : ReserveRepository {

    override fun findNearby(latitude: Double, longitude: Double, radiusKm: Double): List<NearbyReserve> =
        jdbc.query(
            """
            SELECT id, name, region, latitude, longitude,
                   ST_Distance(location, ST_MakePoint(:lng, :lat)::geography) / 1000.0 AS distance_km
            FROM reserves.reserve
            WHERE ST_DWithin(location, ST_MakePoint(:lng, :lat)::geography, :radius_m)
            ORDER BY location <-> ST_MakePoint(:lng, :lat)::geography
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("lat", latitude)
                .addValue("lng", longitude)
                .addValue("radius_m", radiusKm * 1000.0),
        ) { rs, _ -> NearbyReserve(reserve(rs), rs.getDouble("distance_km")) }

    override fun findAll(): List<Reserve> =
        jdbc.query(
            "SELECT id, name, region, latitude, longitude FROM reserves.reserve ORDER BY name",
            MapSqlParameterSource(),
            reserveRowMapper,
        )

    override fun findById(id: ReserveId): Reserve? =
        jdbc.query(
            "SELECT id, name, region, latitude, longitude FROM reserves.reserve WHERE id = :id",
            MapSqlParameterSource("id", id.value),
            reserveRowMapper,
        ).firstOrNull()

    private fun reserve(rs: ResultSet) = Reserve(
        id = ReserveId(rs.getObject("id", UUID::class.java)),
        name = rs.getString("name"),
        region = rs.getString("region"),
        latitude = rs.getDouble("latitude"),
        longitude = rs.getDouble("longitude"),
    )

    private val reserveRowMapper = RowMapper { rs: ResultSet, _: Int -> reserve(rs) }
}
