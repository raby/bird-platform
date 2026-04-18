package com.digitalbluebird.species.domain

data class TaxonomicClassification(
    val kingdom: String,
    val phylum: String,
    val className: String,
    val order: String,
    val family: String,
    val genus: String,
) {
    init {
        listOf(kingdom, phylum, className, order, family, genus).forEach {
            require(it.isNotBlank()) { "taxonomic ranks must not be blank" }
        }
    }
}
