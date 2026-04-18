package com.digitalbluebird.identity.domain

import com.digitalbluebird.shared.domain.UserId
import java.time.Instant

data class User(
    val id: UserId,
    val email: Email,
    val displayName: String,
    val roles: Set<UserRole>,
    val createdAt: Instant,
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(displayName.length <= 80) { "displayName too long" }
        require(roles.isNotEmpty()) { "user must have at least one role" }
    }
}
