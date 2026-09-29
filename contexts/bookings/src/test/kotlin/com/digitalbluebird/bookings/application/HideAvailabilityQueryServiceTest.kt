package com.digitalbluebird.bookings.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.digitalbluebird.bookings.domain.Hide
import com.digitalbluebird.bookings.domain.HideAvailability
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.port.outbound.HideAvailabilityReadModel
import com.digitalbluebird.shared.domain.InstantRange
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class HideAvailabilityQueryServiceTest {

    private val readModel: HideAvailabilityReadModel = mockk()
    private val service = HideAvailabilityQueryService(readModel)

    private val hideId = HideId(UUID.fromString("10000000-0000-0000-0000-000000000001"))
    private val hide = Hide(hideId, "Kingfisher Hide", "Leighton Moss", 6)
    private val slot = InstantRange(Instant.parse("2026-06-02T08:00:00Z"), Instant.parse("2026-06-02T10:00:00Z"))

    @Test
    fun `listHides delegates to the read model`() {
        every { readModel.listHides() } returns listOf(hide)
        assertThat(service.listHides()).isEqualTo(listOf(hide))
    }

    @Test
    fun `availability delegates to the read model`() {
        val availability = HideAvailability(hide, slot, occupied = 2)
        every { readModel.availabilityFor(hideId, slot) } returns availability
        assertThat(service.availability(hideId, slot)).isEqualTo(availability)
    }

    @Test
    fun `availability returns null for an unknown hide`() {
        every { readModel.availabilityFor(hideId, slot) } returns null
        assertThat(service.availability(hideId, slot)).isNull()
    }
}
