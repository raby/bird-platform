package com.digitalbluebird.integrations.taxonomy.domain

import com.digitalbluebird.shared.domain.DomainError

sealed interface TaxonomyError : DomainError {
    data class InvalidName(override val message: String) : TaxonomyError, DomainError.Validation

    data class TaxonNotFound(val scientificName: String) : TaxonomyError, DomainError.NotFound {
        override val message: String = "no taxon for: $scientificName"
    }

    /** The external authority is unreachable and there is no cached value to fall back on (→ 503). */
    data class AuthorityUnavailable(val scientificName: String) : TaxonomyError {
        override val message: String = "taxonomy authority unavailable for: $scientificName"
    }
}
