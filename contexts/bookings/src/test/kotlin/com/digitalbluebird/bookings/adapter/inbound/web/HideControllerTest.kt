package com.digitalbluebird.bookings.adapter.inbound.web

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.bookings.domain.Hide
import com.digitalbluebird.bookings.domain.HideAvailability
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.port.inbound.ViewHideAvailabilityUseCase
import com.digitalbluebird.shared.domain.InstantRange
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class HideControllerTest {

    private val useCase: ViewHideAvailabilityUseCase = mockk()
    private val controller = HideController(useCase)

    private val hideId = HideId(UUID.fromString("10000000-0000-0000-0000-000000000001"))
    private val hide = Hide(hideId, "Kingfisher Hide", "Leighton Moss", 6)
    private val from = "2026-06-02T08:00:00Z"
    private val to = "2026-06-02T10:00:00Z"
    private val slot = InstantRange(Instant.parse(from), Instant.parse(to))

    @Test
    fun `list returns 200 with the hides`() {
        every { useCase.listHides() } returns listOf(hide)

        val response = controller.list()

        assertThat(response.statusCode.value()).isEqualTo(200)
        @Suppress("UNCHECKED_CAST")
        val body = response.body as List<HideResponse>
        assertThat(body).isEqualTo(listOf(HideResponse.from(hide)))
    }

    @Test
    fun `availability returns 200 with occupancy and seats left`() {
        every { useCase.availability(hideId, slot) } returns HideAvailability(hide, slot, occupied = 4)

        val response = controller.availability(hideId.value.toString(), from, to)

        assertThat(response.statusCode.value()).isEqualTo(200)
        val body = response.body as HideAvailabilityResponse
        assertThat(body.occupied).isEqualTo(4)
        assertThat(body.seatsLeft).isEqualTo(2)
    }

    @Test
    fun `availability returns 404 for an unknown hide`() {
        every { useCase.availability(hideId, slot) } returns null

        val response = controller.availability(hideId.value.toString(), from, to)

        assertThat(response.statusCode.value()).isEqualTo(404)
        assertThat((response.body as ErrorResponse).code).isEqualTo("HIDE_NOT_FOUND")
    }

    @Test
    fun `availability returns 400 for a malformed hide id`() {
        val response = controller.availability("not-a-uuid", from, to)

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat((response.body as ErrorResponse).code).isEqualTo("INVALID_ID")
    }

    @Test
    fun `availability returns 400 when the slot is inverted`() {
        val response = controller.availability(hideId.value.toString(), to, from) // from after to

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat((response.body as ErrorResponse).code).isEqualTo("INVALID_SLOT")
    }
}
