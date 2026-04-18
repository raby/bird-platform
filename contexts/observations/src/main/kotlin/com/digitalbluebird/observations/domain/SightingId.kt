package com.digitalbluebird.observations.domain

import java.util.UUID

@JvmInline
value class SightingId(val value: UUID) {
    companion object {
        fun random(): SightingId = SightingId(UUID.randomUUID())
        fun fromString(s: String): SightingId = SightingId(UUID.fromString(s))
    }
}
