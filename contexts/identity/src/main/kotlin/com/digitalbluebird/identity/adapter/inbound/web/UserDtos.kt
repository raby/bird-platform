package com.digitalbluebird.identity.adapter.inbound.web

import com.digitalbluebird.identity.domain.User
import com.digitalbluebird.identity.domain.UserRole
import java.time.Instant

data class CreateUserRequest(
    val email: String,
    val displayName: String,
    val roles: Set<UserRole> = emptySet(),
)

data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val roles: Set<UserRole>,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id.value.toString(),
            email = user.email.value,
            displayName = user.displayName,
            roles = user.roles,
            createdAt = user.createdAt,
        )
    }
}

data class ErrorResponse(val code: String, val message: String)
