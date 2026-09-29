package com.digitalbluebird.bookings.domain

import com.digitalbluebird.shared.domain.InstantRange

/**
 * A bird hide with a fixed seating capacity. There is no Hide aggregate yet (the `reserves` context
 * is an empty scaffold), so hides are seeded reference data for the read model — see V301.
 */
data class Hide(
    val id: HideId,
    val name: String,
    val reserve: String,
    val capacity: Int,
)

/**
 * How full a hide is for a given slot, derived from confirmed bookings by the read model.
 * [occupied] is the sum of confirmed party sizes overlapping [slot]; [seatsLeft] never goes negative.
 */
data class HideAvailability(
    val hide: Hide,
    val slot: InstantRange,
    val occupied: Int,
) {
    val seatsLeft: Int get() = (hide.capacity - occupied).coerceAtLeast(0)
}
