package com.digitalbluebird.shared.domain

sealed interface DomainError {
    val message: String

    interface Validation : DomainError
    interface Conflict : DomainError
    interface NotFound : DomainError
    interface Unauthorized : DomainError
}
