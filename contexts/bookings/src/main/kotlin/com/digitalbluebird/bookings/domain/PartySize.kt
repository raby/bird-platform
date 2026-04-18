package com.digitalbluebird.bookings.domain

@JvmInline
value class PartySize(val value: Int) {
    init {
        require(value in 1..20) { "partySize must be between 1 and 20 (was $value)" }
    }
}
