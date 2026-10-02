package com.digitalbluebird.bookings.adapter.inbound.web

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.bookings.adapter.outbound.persistence.OverlappingSlotException
import com.digitalbluebird.bookings.domain.HideId
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID

class BookingControllerTest {

    // The use-case ports are unused by the exception-handler path, so bare mocks suffice.
    private val controller = BookingController(
        requestBooking = mockk(),
        confirmBooking = mockk(),
        cancelBooking = mockk(),
        findBooking = mockk(),
    )

    @Test
    fun `maps the exclusion-constraint conflict to 409 HideSlotUnavailable`() {
        val hideId = HideId(UUID.fromString("10000000-0000-0000-0000-000000000001"))

        val response = controller.handleOverlappingSlot(OverlappingSlotException(hideId))

        assertThat(response.statusCode.value()).isEqualTo(409)
        // Same code the pre-check path emits (BookingError.HideSlotUnavailable), so the race-loser is
        // indistinguishable from a common-case overlap to the client.
        assertThat((response.body as ErrorResponse).code).isEqualTo("HideSlotUnavailable")
    }
}
