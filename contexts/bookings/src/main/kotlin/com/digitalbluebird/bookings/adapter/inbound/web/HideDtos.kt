package com.digitalbluebird.bookings.adapter.inbound.web

import com.digitalbluebird.bookings.domain.Hide
import com.digitalbluebird.bookings.domain.HideAvailability
import java.time.Instant

data class HideResponse(
    val id: String,
    val name: String,
    val reserve: String,
    val capacity: Int,
) {
    companion object {
        fun from(h: Hide) = HideResponse(
            id = h.id.value.toString(),
            name = h.name,
            reserve = h.reserve,
            capacity = h.capacity,
        )
    }
}

data class HideAvailabilityResponse(
    val hideId: String,
    val name: String,
    val reserve: String,
    val capacity: Int,
    val occupied: Int,
    val seatsLeft: Int,
    val from: Instant,
    val to: Instant,
) {
    companion object {
        fun from(a: HideAvailability) = HideAvailabilityResponse(
            hideId = a.hide.id.value.toString(),
            name = a.hide.name,
            reserve = a.hide.reserve,
            capacity = a.hide.capacity,
            occupied = a.occupied,
            seatsLeft = a.seatsLeft,
            from = a.slot.start,
            to = a.slot.endExclusive,
        )
    }
}
