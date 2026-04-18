package com.digitalbluebird.observations.adapter.inbound.web

import com.digitalbluebird.observations.domain.ObservationError
import com.digitalbluebird.observations.domain.SightingId
import com.digitalbluebird.observations.domain.port.inbound.FindSightingUseCase
import com.digitalbluebird.observations.domain.port.inbound.RecordSightingCommand
import com.digitalbluebird.observations.domain.port.inbound.RecordSightingUseCase
import com.digitalbluebird.shared.domain.DomainError
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/sightings")
class SightingController(
    private val recordSighting: RecordSightingUseCase,
    private val findSighting: FindSightingUseCase,
) {

    @PostMapping
    fun record(@RequestBody request: RecordSightingRequest): ResponseEntity<Any> =
        recordSighting.record(
            RecordSightingCommand(
                observerId = request.observerId,
                speciesId = request.speciesId,
                latitude = request.latitude,
                longitude = request.longitude,
                observedAt = request.observedAt,
                count = request.count,
                notes = request.notes,
            ),
        ).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.status(201).body(SightingResponse.from(it)) },
        )

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Any> =
        runCatching { SightingId(UUID.fromString(id)) }.fold(
            onSuccess = { sightingId ->
                findSighting.findById(sightingId).fold(
                    ifLeft = { it.toResponse() },
                    ifRight = { ResponseEntity.ok(SightingResponse.from(it)) },
                )
            },
            onFailure = {
                ResponseEntity.badRequest().body(ErrorResponse("INVALID_ID", "id is not a valid UUID"))
            },
        )

    private fun ObservationError.toResponse(): ResponseEntity<Any> {
        val body = ErrorResponse(code = this::class.simpleName ?: "ERROR", message = message)
        val status = when (this) {
            is DomainError.Validation -> 400
            is DomainError.Conflict -> 409
            is DomainError.NotFound -> 404
            is DomainError.Unauthorized -> 401
            else -> 500
        }
        return ResponseEntity.status(status).body(body)
    }
}
