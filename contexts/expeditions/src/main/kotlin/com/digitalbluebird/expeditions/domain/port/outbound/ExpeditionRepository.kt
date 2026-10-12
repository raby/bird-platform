package com.digitalbluebird.expeditions.domain.port.outbound

import com.digitalbluebird.expeditions.domain.Expedition
import com.digitalbluebird.expeditions.domain.ExpeditionId

interface ExpeditionRepository {
    /** Persist the expedition and its legs (upsert), so each step of the orchestration is durable. */
    fun save(expedition: Expedition)

    fun findById(id: ExpeditionId): Expedition?

    /** The expedition owning the leg with this booking id — correlates a booking event back to it. */
    fun findByLegBookingId(bookingId: String): Expedition?
}
