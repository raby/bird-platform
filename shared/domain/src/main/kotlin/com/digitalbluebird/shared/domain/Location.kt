package com.digitalbluebird.shared.domain

@JvmInline
value class Latitude(val value: Double) {
    init {
        require(value in -90.0..90.0) { "Latitude must be in [-90, 90], was $value" }
    }
}

@JvmInline
value class Longitude(val value: Double) {
    init {
        require(value in -180.0..180.0) { "Longitude must be in [-180, 180], was $value" }
    }
}

data class Location(val latitude: Latitude, val longitude: Longitude) {
    companion object {
        fun of(lat: Double, lng: Double): Location = Location(Latitude(lat), Longitude(lng))
    }
}
