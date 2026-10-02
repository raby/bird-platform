package com.digitalbluebird.bookings.adapter.inbound.web

import com.digitalbluebird.bookings.adapter.outbound.persistence.OverlappingSlotException
import com.digitalbluebird.bookings.domain.BookingError
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.port.inbound.CancelBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.ConfirmBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.FindBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingCommand
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingUseCase
import com.digitalbluebird.shared.domain.DomainError
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/bookings")
class BookingController(
    private val requestBooking: RequestBookingUseCase,
    private val confirmBooking: ConfirmBookingUseCase,
    private val cancelBooking: CancelBookingUseCase,
    private val findBooking: FindBookingUseCase,
) {

    @PostMapping
    fun request(@RequestBody request: RequestBookingRequest): ResponseEntity<Any> =
        requestBooking.request(
            RequestBookingCommand(
                idempotencyKey = request.idempotencyKey,
                hideId = request.hideId,
                observerId = request.observerId,
                slotStart = request.slotStart,
                slotEnd = request.slotEnd,
                partySize = request.partySize,
            ),
        ).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.status(201).body(BookingResponse.from(it)) },
        )

    @PostMapping("/{id}/confirm")
    fun confirm(
        @PathVariable id: String,
        @RequestBody body: TransitionBookingRequest,
    ): ResponseEntity<Any> = withBookingId(id) { bookingId ->
        confirmBooking.confirm(bookingId, body.expectedVersion).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.ok(BookingResponse.from(it)) },
        )
    }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: String,
        @RequestBody body: TransitionBookingRequest,
    ): ResponseEntity<Any> = withBookingId(id) { bookingId ->
        cancelBooking.cancel(bookingId, body.expectedVersion).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.ok(BookingResponse.from(it)) },
        )
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Any> = withBookingId(id) { bookingId ->
        findBooking.findById(bookingId).fold(
            ifLeft = { it.toResponse() },
            ifRight = { ResponseEntity.ok(BookingResponse.from(it)) },
        )
    }

    // The V303 exclusion constraint rejected an overlapping active booking under a race that passed
    // BookingService's pre-check (JdbcBookingRepository throws OverlappingSlotException; the failed
    // INSERT rolled the request's transaction back). Map it to the same 409 + "HideSlotUnavailable"
    // body the pre-check path returns via BookingError.HideSlotUnavailable, so the race-loser and the
    // common-case overlap are indistinguishable to the client.
    @ExceptionHandler(OverlappingSlotException::class)
    fun handleOverlappingSlot(e: OverlappingSlotException): ResponseEntity<Any> =
        ResponseEntity.status(409).body(ErrorResponse(code = "HideSlotUnavailable", message = e.message ?: "hide slot unavailable"))

    private fun withBookingId(id: String, block: (BookingId) -> ResponseEntity<Any>): ResponseEntity<Any> =
        runCatching { BookingId(UUID.fromString(id)) }.fold(
            onSuccess = block,
            onFailure = {
                ResponseEntity.badRequest().body(ErrorResponse("INVALID_ID", "id is not a valid UUID"))
            },
        )

    private fun BookingError.toResponse(): ResponseEntity<Any> {
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
