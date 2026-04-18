package com.digitalbluebird.shared.domain

@JvmInline
value class SpeciesId(val value: String) {
    init {
        require(value.isNotBlank()) { "SpeciesId cannot be blank" }
    }
}
