package com.digitalbluebird.expeditions.domain

import com.digitalbluebird.shared.domain.DomainError

sealed interface ExpeditionError : DomainError {
    data class InvalidLeader(override val message: String) : ExpeditionError, DomainError.Validation
    data class InvalidPartySize(override val message: String) : ExpeditionError, DomainError.Validation
    data class NoHides(override val message: String) : ExpeditionError, DomainError.Validation
    data class InvalidSlot(override val message: String) : ExpeditionError, DomainError.Validation
    data class InvalidExpeditionId(override val message: String) : ExpeditionError, DomainError.Validation

    data class ExpeditionNotFound(val id: ExpeditionId) : ExpeditionError, DomainError.NotFound {
        override val message: String = "expedition not found: ${id.value}"
    }
}
