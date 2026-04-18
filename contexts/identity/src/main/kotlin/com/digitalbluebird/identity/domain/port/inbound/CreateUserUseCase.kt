package com.digitalbluebird.identity.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.identity.domain.IdentityError
import com.digitalbluebird.identity.domain.User
import com.digitalbluebird.identity.domain.UserRole

interface CreateUserUseCase {
    fun createUser(command: CreateUserCommand): Either<IdentityError, User>
}

data class CreateUserCommand(
    val email: String,
    val displayName: String,
    val roles: Set<UserRole>,
)
