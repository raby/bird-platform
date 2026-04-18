package com.digitalbluebird.species.adapter.outbound.taxonomy

internal data class ExternalSpeciesDto(
    val sci_name: String,
    val en_name: String,
    val nl_name: String?,
    val order_: String,
    val fam: String,
    val gen: String,
    val iucn: String?,
)
