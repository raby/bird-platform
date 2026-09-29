package com.digitalbluebird.bookings.domain.port.outbound

import com.digitalbluebird.bookings.domain.BookingId
import com.digitalbluebird.bookings.domain.Hide
import com.digitalbluebird.bookings.domain.HideAvailability
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.PartySize
import com.digitalbluebird.shared.domain.InstantRange
import com.digitalbluebird.shared.domain.ObserverId
import java.time.Instant

/**
 * The hide-availability read model (CQRS): a denormalized projection of **confirmed** bookings plus
 * seeded hide capacity, kept separate from the write model. The projection ops are idempotent so the
 * at-least-once outbox relay can safely re-deliver; the query ops serve the UI.
 */
interface HideAvailabilityReadModel {
    /** Record (or re-record, keyed by booking) that a confirmed booking occupies a hide slot. */
    fun applyConfirmed(stay: ConfirmedStay)

    /** Free a booking's occupancy — on cancel, or a no-op if it was never confirmed. */
    fun removeByBooking(bookingId: BookingId)

    /** All seeded hides, for choosing one. */
    fun listHides(): List<Hide>

    /** Availability of one hide over [slot], or `null` if the hide is unknown. */
    fun availabilityFor(hideId: HideId, slot: InstantRange): HideAvailability?
}

/** A confirmed booking's occupancy of a hide slot — the input to the availability projection. */
data class ConfirmedStay(
    val bookingId: BookingId,
    val hideId: HideId,
    val observerId: ObserverId,
    val slot: InstantRange,
    val partySize: PartySize,
    val confirmedAt: Instant,
)
