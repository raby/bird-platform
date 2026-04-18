package com.digitalbluebird.shared.domain

import java.util.UUID

@JvmInline
value class UserId(val value: UUID) {
    companion object {
        fun random(): UserId = UserId(UUID.randomUUID())
        fun fromString(s: String): UserId = UserId(UUID.fromString(s))
    }
}
