package com.digitalbluebird.integrations.taxonomy.adapter.inbound.web

import com.digitalbluebird.integrations.taxonomy.domain.TaxonResult
import java.time.Instant

data class TaxonResponse(
    val scientificName: String,
    val commonName: String,
    val family: String,
    val order: String,
    val conservationStatus: String,
    val source: String,
    val fetchedAt: Instant,
) {
    companion object {
        fun from(r: TaxonResult) = TaxonResponse(
            scientificName = r.taxon.scientificName,
            commonName = r.taxon.commonName,
            family = r.taxon.family,
            order = r.taxon.order,
            conservationStatus = r.taxon.conservationStatus,
            source = r.source.name,
            fetchedAt = r.fetchedAt,
        )
    }
}

data class ErrorResponse(val code: String, val message: String)
