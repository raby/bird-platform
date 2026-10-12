package com.digitalbluebird.reserves.domain

import com.digitalbluebird.shared.domain.DomainError

sealed interface ReserveError : DomainError {
    data class InvalidCoordinates(override val message: String) : ReserveError, DomainError.Validation
    data class InvalidRadius(override val message: String) : ReserveError, DomainError.Validation
    data class InvalidReserveId(override val message: String) : ReserveError, DomainError.Validation

    data class ReserveNotFound(val id: ReserveId) : ReserveError, DomainError.NotFound {
        override val message: String = "reserve not found: ${id.value}"
    }
}
