package com.digitalbluebird.integrations.taxonomy.adapter.inbound.web

import com.digitalbluebird.integrations.taxonomy.adapter.outbound.authority.StubTaxonomyAuthority
import com.digitalbluebird.integrations.taxonomy.domain.TaxonomyError
import com.digitalbluebird.integrations.taxonomy.domain.port.inbound.LookupTaxonUseCase
import com.digitalbluebird.shared.domain.DomainError
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/integrations/taxonomy")
class TaxonController(
    private val lookupTaxon: LookupTaxonUseCase,
    // The concrete stub, only for the demo offline toggle below — a real deployment has no such control.
    private val authority: StubTaxonomyAuthority,
) {
    @GetMapping
    fun lookup(@RequestParam name: String): ResponseEntity<Any> =
        lookupTaxon.lookup(name).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.ok(TaxonResponse.from(it)) },
        )

    /** Demo control: simulate the authority going offline, to show the cache's stale-fallback. */
    @PostMapping("/offline")
    fun offline(@RequestParam value: Boolean): Map<String, Boolean> {
        authority.setOffline(value)
        return mapOf("offline" to value)
    }

    private fun TaxonomyError.toResponse(): ResponseEntity<Any> {
        val status = when (this) {
            is TaxonomyError.AuthorityUnavailable -> 503
            is DomainError.Validation -> 400
            is DomainError.NotFound -> 404
            else -> 500
        }
        return ResponseEntity.status(status).body(ErrorResponse(this::class.simpleName ?: "ERROR", message))
    }
}
