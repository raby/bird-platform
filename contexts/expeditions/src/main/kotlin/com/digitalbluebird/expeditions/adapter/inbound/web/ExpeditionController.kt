package com.digitalbluebird.expeditions.adapter.inbound.web

import com.digitalbluebird.expeditions.domain.ExpeditionError
import com.digitalbluebird.expeditions.domain.port.inbound.HideRequest
import com.digitalbluebird.expeditions.domain.port.inbound.PlanExpeditionCommand
import com.digitalbluebird.expeditions.domain.port.inbound.PlanExpeditionUseCase
import com.digitalbluebird.expeditions.domain.port.inbound.ViewExpeditionUseCase
import com.digitalbluebird.shared.domain.DomainError
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/expeditions")
class ExpeditionController(
    private val planExpedition: PlanExpeditionUseCase,
    private val viewExpedition: ViewExpeditionUseCase,
) {
    @PostMapping
    fun plan(@RequestBody body: PlanExpeditionRequest): ResponseEntity<Any> =
        planExpedition.plan(
            PlanExpeditionCommand(
                leaderId = body.leaderId,
                partySize = body.partySize,
                hides = body.hides.map { HideRequest(it.hideId, it.slotStart, it.slotEnd) },
            ),
        ).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.status(201).body(ExpeditionResponse.from(it)) },
        )

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Any> =
        viewExpedition.findById(id).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.ok(ExpeditionResponse.from(it)) },
        )

    private fun ExpeditionError.toResponse(): ResponseEntity<Any> {
        val status = when (this) {
            is DomainError.Validation -> 400
            is DomainError.NotFound -> 404
            is DomainError.Conflict -> 409
            else -> 500
        }
        return ResponseEntity.status(status).body(ErrorResponse(this::class.simpleName ?: "ERROR", message))
    }
}
