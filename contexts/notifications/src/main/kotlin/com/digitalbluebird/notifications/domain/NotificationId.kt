package com.digitalbluebird.notifications.domain

import java.util.UUID

@JvmInline
value class NotificationId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): NotificationId = NotificationId(UUID.randomUUID())
    }
}
