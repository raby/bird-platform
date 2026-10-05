package com.digitalbluebird.surveys.domain

/** A survey season, identified by its calendar year (e.g. the 2026 breeding season). */
@JvmInline
value class Season(val year: Int) {
    init {
        require(year in 1900..2200) { "season year out of range: $year" }
    }
}
