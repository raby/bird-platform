package com.digitalbluebird.bookings.domain.port.inbound

import com.digitalbluebird.bookings.domain.Hide
import com.digitalbluebird.bookings.domain.HideAvailability
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.shared.domain.InstantRange

/** The read side of hides: list them, and check how full one is for a slot. Backs the UI's hide picker. */
interface ViewHideAvailabilityUseCase {
    fun listHides(): List<Hide>

    /** Availability of one hide over [slot], or `null` if the hide is unknown. */
    fun availability(hideId: HideId, slot: InstantRange): HideAvailability?
}
