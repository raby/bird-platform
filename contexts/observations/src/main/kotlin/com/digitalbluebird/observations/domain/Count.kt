package com.digitalbluebird.observations.domain

@JvmInline
value class Count(val value: Int) {
    init {
        require(value in 1..10_000) { "count must be in 1..10000, was $value" }
    }
}
