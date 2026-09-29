package com.digitalbluebird.bookings.adapter.inbound.web

import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.port.inbound.ViewHideAvailabilityUseCase
import com.digitalbluebird.shared.domain.InstantRange
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/hides")
class HideController(
    private val viewHideAvailability: ViewHideAvailabilityUseCase,
) {

    @GetMapping
    fun list(): ResponseEntity<Any> =
        ResponseEntity.ok(viewHideAvailability.listHides().map(HideResponse::from))

    @GetMapping("/{id}/availability")
    fun availability(
        @PathVariable id: String,
        @RequestParam("from") from: String,
        @RequestParam("to") to: String,
    ): ResponseEntity<Any> {
        val hideId = runCatching { HideId(UUID.fromString(id)) }.getOrElse {
            return badRequest("INVALID_ID", "id is not a valid UUID")
        }
        val slot = runCatching { InstantRange(Instant.parse(from), Instant.parse(to)) }.getOrElse {
            return badRequest("INVALID_SLOT", it.message ?: "from/to must be ISO-8601 instants with from before to")
        }
        val availability = viewHideAvailability.availability(hideId, slot)
            ?: return ResponseEntity.status(404).body(ErrorResponse("HIDE_NOT_FOUND", "hide not found: $id"))
        return ResponseEntity.ok(HideAvailabilityResponse.from(availability))
    }

    private fun badRequest(code: String, message: String): ResponseEntity<Any> =
        ResponseEntity.badRequest().body(ErrorResponse(code, message))
}
