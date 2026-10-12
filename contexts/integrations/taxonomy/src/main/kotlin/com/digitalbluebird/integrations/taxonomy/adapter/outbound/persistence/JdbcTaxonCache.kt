package com.digitalbluebird.integrations.taxonomy.adapter.outbound.persistence

import com.digitalbluebird.integrations.taxonomy.domain.CachedTaxon
import com.digitalbluebird.integrations.taxonomy.domain.Taxon
import com.digitalbluebird.integrations.taxonomy.domain.port.outbound.TaxonCache
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

class JdbcTaxonCache(
    private val jdbc: NamedParameterJdbcTemplate,
) : TaxonCache {

    override fun get(scientificName: String): CachedTaxon? =
        jdbc.query(
            """
            SELECT scientific_name, common_name, family, taxon_order, conservation_status, fetched_at
            FROM taxonomy.taxon_cache WHERE scientific_name = :name
            """.trimIndent(),
            MapSqlParameterSource("name", scientificName),
            rowMapper,
        ).firstOrNull()

    override fun put(taxon: Taxon, fetchedAt: Instant) {
        jdbc.update(
            """
            INSERT INTO taxonomy.taxon_cache
                (scientific_name, common_name, family, taxon_order, conservation_status, fetched_at)
            VALUES (:name, :common, :family, :ordr, :iucn, :fetched_at)
            ON CONFLICT (scientific_name) DO UPDATE SET
                common_name = :common, family = :family, taxon_order = :ordr,
                conservation_status = :iucn, fetched_at = :fetched_at
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("name", taxon.scientificName)
                .addValue("common", taxon.commonName)
                .addValue("family", taxon.family)
                .addValue("ordr", taxon.order)
                .addValue("iucn", taxon.conservationStatus)
                .addValue("fetched_at", Timestamp.from(fetchedAt)),
        )
    }

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        CachedTaxon(
            taxon = Taxon(
                scientificName = rs.getString("scientific_name"),
                commonName = rs.getString("common_name"),
                family = rs.getString("family"),
                order = rs.getString("taxon_order"),
                conservationStatus = rs.getString("conservation_status"),
            ),
            fetchedAt = rs.getTimestamp("fetched_at").toInstant(),
        )
    }
}
