package com.digitalbluebird.reserves.adapter.inbound.web

import com.digitalbluebird.reserves.domain.ReserveError
import com.digitalbluebird.reserves.domain.port.inbound.FindReservesUseCase
import com.digitalbluebird.shared.domain.DomainError
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/reserves")
class ReserveController(
    private val findReserves: FindReservesUseCase,
) {
    @GetMapping
    fun all(): List<ReserveResponse> = findReserves.all().map(ReserveResponse::from)

    @GetMapping("/nearby")
    fun nearby(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "50") radiusKm: Double,
    ): ResponseEntity<Any> = findReserves.nearby(lat, lng, radiusKm).fold(
        ifLeft = { it.toResponse() },
        ifRight = { nearby -> ResponseEntity.ok(nearby.map(NearbyReserveResponse::from)) },
    )

    @GetMapping("/{id}")
    fun byId(@PathVariable id: String): ResponseEntity<Any> = findReserves.byId(id).fold(
        ifLeft = { it.toResponse() },
        ifRight = { ResponseEntity.ok(ReserveResponse.from(it)) },
    )

    private fun ReserveError.toResponse(): ResponseEntity<Any> {
        val status = when (this) {
            is DomainError.Validation -> 400
            is DomainError.NotFound -> 404
            else -> 500
        }
        return ResponseEntity.status(status).body(ErrorResponse(this::class.simpleName ?: "ERROR", message))
    }
}
