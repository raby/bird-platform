package com.digitalbluebird.integrations.taxonomy.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.integrations.taxonomy.domain.TaxonResult
import com.digitalbluebird.integrations.taxonomy.domain.TaxonomyError

/**
 * Look up a taxon by scientific name through a read-through cache: a fresh cached value is returned as
 * is; a miss or an expired entry triggers a fetch from the authority (then cached); and if the
 * authority is unreachable, a stale cached value is served rather than failing. The [TaxonResult] says
 * which of those happened.
 */
fun interface LookupTaxonUseCase {
    fun lookup(scientificName: String): Either<TaxonomyError, TaxonResult>
}
