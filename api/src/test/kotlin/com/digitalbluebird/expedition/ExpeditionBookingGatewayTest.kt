package com.digitalbluebird.expedition

import arrow.core.left
import arrow.core.right
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.digitalbluebird.bookings.domain.Booking
import com.digitalbluebird.bookings.domain.BookingError
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.BookingStatus
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.PartySize
import com.digitalbluebird.bookings.domain.port.inbound.CancelBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.ConfirmBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.FindBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingUseCase
import com.digitalbluebird.expeditions.domain.port.outbound.BookingResult
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ExpeditionBookingGatewayTest {

    private val requestBooking = mockk<RequestBookingUseCase>()
    private val confirmBooking = mockk<ConfirmBookingUseCase>()
    private val cancelBooking = mockk<CancelBookingUseCase>(relaxed = true)
    private val findBooking = mockk<FindBookingUseCase>()
    private val gateway = ExpeditionBookingGateway(requestBooking, confirmBooking, cancelBooking, findBooking)

    private val slotStart = Instant.parse("2026-11-01T08:00:00Z")
    private val slotEnd = Instant.parse("2026-11-01T10:00:00Z")

    private fun booking(id: UUID, status: BookingStatus, version: Long) = Booking(
        id = BookingId(id),
        hideId = HideId(UUID.randomUUID()),
        observerId = ObserverId(UUID.randomUUID()),
        slot = InstantRange(slotStart, slotEnd),
        partySize = PartySize(2),
        status = status,
        version = version,
        createdAt = slotStart,
        updatedAt = slotStart,
    )

    @Test
    fun `book requests then confirms, returning the booking id`() {
        val id = UUID.randomUUID()
        every { requestBooking.request(any()) } returns booking(id, BookingStatus.REQUESTED, 0).right()
        every { confirmBooking.confirm(BookingId(id), 0) } returns booking(id, BookingStatus.CONFIRMED, 1).right()

        val result = gateway.book("hide-A", UUID.randomUUID().toString(), slotStart, slotEnd, 2)

        assertThat(result).isInstanceOf(BookingResult.Booked::class)
        assertThat((result as BookingResult.Booked).bookingId).isEqualTo(id.toString())
    }

    @Test
    fun `book reports Unavailable when the request is refused, without confirming`() {
        every { requestBooking.request(any()) } returns BookingError.HideSlotUnavailable(HideId(UUID.randomUUID())).left()

        val result = gateway.book("hide-A", UUID.randomUUID().toString(), slotStart, slotEnd, 2)

        assertThat(result).isEqualTo(BookingResult.Unavailable)
        verify(exactly = 0) { confirmBooking.confirm(any(), any()) }
    }

    @Test
    fun `book releases the requested booking if confirm fails, then reports Unavailable`() {
        val id = UUID.randomUUID()
        every { requestBooking.request(any()) } returns booking(id, BookingStatus.REQUESTED, 0).right()
        every { confirmBooking.confirm(BookingId(id), 0) } returns BookingError.VersionConflict(BookingId(id), 0).left()

        val result = gateway.book("hide-A", UUID.randomUUID().toString(), slotStart, slotEnd, 2)

        assertThat(result).isEqualTo(BookingResult.Unavailable)
        verify(exactly = 1) { cancelBooking.cancel(BookingId(id), 0) } // orphan released
    }

    @Test
    fun `cancel finds the booking and cancels it when still active`() {
        val id = UUID.randomUUID()
        every { findBooking.findById(BookingId(id)) } returns booking(id, BookingStatus.CONFIRMED, 1).right()

        gateway.cancel(id.toString())

        verify(exactly = 1) { cancelBooking.cancel(BookingId(id), 1) }
    }

    @Test
    fun `cancel is a no-op when the booking is already cancelled`() {
        val id = UUID.randomUUID()
        every { findBooking.findById(BookingId(id)) } returns booking(id, BookingStatus.CANCELLED, 2).right()

        gateway.cancel(id.toString())

        verify(exactly = 0) { cancelBooking.cancel(any(), any()) }
    }

    @Test
    fun `cancel is a no-op when the booking is not found`() {
        val id = UUID.randomUUID()
        every { findBooking.findById(BookingId(id)) } returns BookingError.BookingNotFound(BookingId(id)).left()

        gateway.cancel(id.toString())

        verify(exactly = 0) { cancelBooking.cancel(any(), any()) }
    }
}
