package com.digitalbluebird.identity.adapter.inbound.web

import com.digitalbluebird.identity.domain.IdentityError
import com.digitalbluebird.identity.domain.port.inbound.CreateUserCommand
import com.digitalbluebird.identity.domain.port.inbound.CreateUserUseCase
import com.digitalbluebird.identity.domain.port.inbound.FindUserUseCase
import com.digitalbluebird.shared.domain.DomainError
import com.digitalbluebird.shared.domain.UserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/users")
class UserController(
    private val createUser: CreateUserUseCase,
    private val findUser: FindUserUseCase,
) {

    @PostMapping
    fun create(@RequestBody request: CreateUserRequest): ResponseEntity<Any> =
        createUser.createUser(
            CreateUserCommand(
                email = request.email,
                displayName = request.displayName,
                roles = request.roles,
            ),
        ).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.status(201).body(UserResponse.from(it)) },
        )

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Any> =
        runCatching { UserId(UUID.fromString(id)) }.fold(
            onSuccess = { userId ->
                findUser.findById(userId).fold(
                    ifLeft = { it.toResponse() },
                    ifRight = { ResponseEntity.ok(UserResponse.from(it)) },
                )
            },
            onFailure = {
                ResponseEntity.badRequest().body(ErrorResponse("INVALID_ID", "id is not a valid UUID"))
            },
        )

    private fun IdentityError.toResponse(): ResponseEntity<Any> {
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
