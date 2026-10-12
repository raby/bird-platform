package com.digitalbluebird.expeditions.domain

import java.time.Instant

/**
 * One hide the expedition needs: its slot, the booking that holds it once made, and where it stands.
 * PENDING = not yet attempted, BOOKED = held, CANCELLED = released during compensation, UNAVAILABLE =
 * the slot could not be held (the clash that fails the whole expedition).
 */
data class ExpeditionLeg(
    val hideId: String,
    val slotStart: Instant,
    val slotEnd: Instant,
    val bookingId: String?,
    val status: LegStatus,
)

enum class LegStatus { PENDING, BOOKED, CANCELLED, UNAVAILABLE }
