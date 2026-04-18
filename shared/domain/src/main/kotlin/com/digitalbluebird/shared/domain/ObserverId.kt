package com.digitalbluebird.shared.domain

import java.util.UUID

@JvmInline
value class ObserverId(val value: UUID) {
    companion object {
        fun random(): ObserverId = ObserverId(UUID.randomUUID())
        fun fromString(s: String): ObserverId = ObserverId(UUID.fromString(s))
    }
}
