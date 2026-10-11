package com.digitalbluebird.integrations.ebird.adapter.outbound.ebird

/**
 * eBird's own submission shape — its field names (speciesCode, obsDt, howMany), its formats. It lives
 * here in the adapter and never in the domain: that separation is the anti-corruption layer. A real
 * adapter serialises this to eBird's API; the stub logs it.
 */
data class EbirdChecklist(
    val speciesCode: String,
    val latitude: String,
    val longitude: String,
    val obsDt: String,
    val howMany: Int,
    val observationId: String,
)
