package com.digitalbluebird.expeditions.domain

import java.util.UUID

@JvmInline
value class ExpeditionId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): ExpeditionId = ExpeditionId(UUID.randomUUID())
        fun fromString(s: String): ExpeditionId = ExpeditionId(UUID.fromString(s))
    }
}
