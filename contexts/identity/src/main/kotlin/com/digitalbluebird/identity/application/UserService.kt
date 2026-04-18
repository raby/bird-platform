package com.digitalbluebird.identity.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.digitalbluebird.identity.domain.Email
import com.digitalbluebird.identity.domain.IdentityError
import com.digitalbluebird.identity.domain.User
import com.digitalbluebird.identity.domain.port.inbound.CreateUserCommand
import com.digitalbluebird.identity.domain.port.inbound.CreateUserUseCase
import com.digitalbluebird.identity.domain.port.inbound.FindUserUseCase
import com.digitalbluebird.identity.domain.port.outbound.UserRepository
import com.digitalbluebird.shared.domain.UserId
import java.time.Clock

class UserService(
    private val users: UserRepository,
    private val clock: Clock,
) : CreateUserUseCase, FindUserUseCase {

    override fun createUser(command: CreateUserCommand): Either<IdentityError, User> {
        val email = try {
            Email.of(command.email)
        } catch (e: IllegalArgumentException) {
            return IdentityError.InvalidEmail(e.message ?: "invalid email").left()
        }

        val displayName = command.displayName.trim()
        if (displayName.isBlank() || displayName.length > 80) {
            return IdentityError.InvalidDisplayName("displayName must be 1..80 chars").left()
        }

        if (users.existsByEmail(email)) {
            return IdentityError.EmailAlreadyRegistered(email.value).left()
        }

        val user = User(
            id = UserId.random(),
            email = email,
            displayName = displayName,
            roles = command.roles.ifEmpty { setOf(com.digitalbluebird.identity.domain.UserRole.OBSERVER) },
            createdAt = clock.instant(),
        )
        return users.save(user).right()
    }

    override fun findById(id: UserId): Either<IdentityError, User> =
        users.findById(id)?.right() ?: IdentityError.UserNotFound(id).left()
}
