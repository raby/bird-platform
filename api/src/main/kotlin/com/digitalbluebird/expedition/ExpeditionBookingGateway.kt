package com.digitalbluebird.expedition

import arrow.core.getOrElse
import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.BookingStatus
import com.digitalbluebird.bookings.domain.port.inbound.CancelBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.ConfirmBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.FindBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingCommand
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingUseCase
import com.digitalbluebird.expeditions.domain.port.outbound.BookingResult
import com.digitalbluebird.expeditions.domain.port.outbound.HideBookingGateway
import java.time.Instant
import java.util.UUID

/**
 * Adapts the expeditions context's [HideBookingGateway] to the bookings context. It lives in the api
 * composition root — the one place allowed to depend on both — so neither context imports the other.
 * Each [book] is a real bookings request + confirm (its own local transaction); a failure at either
 * step (a slot clash, say) is reported as [BookingResult.Unavailable] for the process manager to
 * compensate around.
 */
class ExpeditionBookingGateway(
    private val requestBooking: RequestBookingUseCase,
    private val confirmBooking: ConfirmBookingUseCase,
    private val cancelBooking: CancelBookingUseCase,
    private val findBooking: FindBookingUseCase,
) : HideBookingGateway {

    override fun book(
        hideId: String,
        observerId: String,
        slotStart: Instant,
        slotEnd: Instant,
        partySize: Int,
    ): BookingResult {
        val requested = requestBooking.request(
            RequestBookingCommand(
                idempotencyKey = UUID.randomUUID().toString(),
                hideId = hideId,
                observerId = observerId,
                slotStart = slotStart,
                slotEnd = slotEnd,
                partySize = partySize,
            ),
        ).getOrElse { return BookingResult.Unavailable }

        val confirmed = confirmBooking.confirm(requested.id, requested.version)
            .getOrElse {
                // Confirm failed after the request succeeded (a rare race / version conflict). Release the
                // orphaned REQUESTED booking so it does not hold the slot, then report the leg unavailable
                // for the process manager to compensate around.
                cancelBooking.cancel(requested.id, requested.version)
                return BookingResult.Unavailable
            }

        return BookingResult.Booked(confirmed.id.value.toString())
    }

    override fun cancel(bookingId: String) {
        val booking = findBooking.findById(BookingId(UUID.fromString(bookingId))).getOrNull() ?: return
        // Read for the current version, then cancel — unless it is already cancelled (idempotent).
        if (booking.status != BookingStatus.CANCELLED) {
            cancelBooking.cancel(booking.id, booking.version)
        }
    }
}
