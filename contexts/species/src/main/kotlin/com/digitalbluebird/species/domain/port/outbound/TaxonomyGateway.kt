package com.digitalbluebird.species.domain.port.outbound

import com.digitalbluebird.species.domain.Species

interface TaxonomyGateway {
    fun fetchSpecies(): List<Species>
}
