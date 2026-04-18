package com.digitalbluebird.species.domain

import com.digitalbluebird.shared.domain.DomainError
import com.digitalbluebird.shared.domain.SpeciesId

sealed interface SpeciesError : DomainError {
    data class InvalidQuery(override val message: String) : SpeciesError, DomainError.Validation
    data class SpeciesNotFound(val id: SpeciesId) : SpeciesError, DomainError.NotFound {
        override val message: String = "species not found: ${id.value}"
    }
    data class TaxonomyImportFailed(override val message: String, val cause: Throwable? = null) : SpeciesError {
        override fun toString(): String = "TaxonomyImportFailed(message=$message)"
    }
}
