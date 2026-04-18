package com.digitalbluebird.species.adapter.inbound.web

import com.digitalbluebird.shared.domain.DomainError
import com.digitalbluebird.species.domain.SpeciesError
import com.digitalbluebird.species.domain.port.inbound.ImportTaxonomyUseCase
import com.digitalbluebird.species.domain.port.inbound.SearchSpeciesUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/species")
class SpeciesController(
    private val importTaxonomy: ImportTaxonomyUseCase,
    private val searchSpecies: SearchSpeciesUseCase,
) {

    @PostMapping("/import")
    fun import(): ResponseEntity<Any> =
        importTaxonomy.importAll().fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.ok(ImportResponse(it.imported)) },
        )

    @GetMapping("/search")
    fun search(
        @RequestParam("q") query: String,
        @RequestParam("limit", defaultValue = "20") limit: Int,
    ): ResponseEntity<Any> =
        searchSpecies.search(query, limit).fold(
            ifLeft = { it.toResponse() },
            ifRight = { list -> ResponseEntity.ok(list.map(SpeciesResponse::from)) },
        )

    private fun SpeciesError.toResponse(): ResponseEntity<Any> {
        val body = ErrorResponse(code = this::class.simpleName ?: "ERROR", message = message)
        val status = when (this) {
            is DomainError.Validation -> 400
            is DomainError.NotFound -> 404
            is SpeciesError.TaxonomyImportFailed -> 502
            else -> 500
        }
        return ResponseEntity.status(status).body(body)
    }
}
