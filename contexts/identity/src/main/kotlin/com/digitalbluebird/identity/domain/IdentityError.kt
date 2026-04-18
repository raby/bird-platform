package com.digitalbluebird.identity.domain

import com.digitalbluebird.shared.domain.DomainError
import com.digitalbluebird.shared.domain.UserId

sealed interface IdentityError : DomainError {
    data class InvalidEmail(override val message: String) : IdentityError, DomainError.Validation
    data class InvalidDisplayName(override val message: String) : IdentityError, DomainError.Validation
    data class EmailAlreadyRegistered(val email: String) : IdentityError, DomainError.Conflict {
        override val message: String = "email already registered: $email"
    }
    data class UserNotFound(val id: UserId) : IdentityError, DomainError.NotFound {
        override val message: String = "user not found: ${id.value}"
    }
}
