package com.digitalbluebird.species.domain

data class CommonName(val language: String, val name: String) {
    init {
        require(language.length == 2 && language.all { it.isLowerCase() }) {
            "language must be ISO-639-1 two-letter lowercase code, was '$language'"
        }
        require(name.isNotBlank()) { "common name must not be blank" }
        require(name.length <= 120) { "common name too long" }
    }
}
