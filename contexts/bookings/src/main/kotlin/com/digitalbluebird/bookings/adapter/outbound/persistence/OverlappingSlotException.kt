package com.digitalbluebird.bookings.adapter.outbound.persistence

import com.digitalbluebird.bookings.domain.HideId

/**
 * Thrown by [JdbcBookingRepository.insert] when the `bookings_no_overlapping_active_slot` exclusion
 * constraint (migration V303) rejects a booking because an active booking already overlaps the slot
 * on that hide — the storage-layer backstop firing under a race that slipped past the service's
 * hasOverlappingActiveBooking pre-check.
 *
 * It carries the DB-specific detail (the Postgres 23P01 violation) out of the persistence adapter as
 * a semantic signal: the failed INSERT has aborted the transaction, so the service lets this
 * propagate (triggering the @Transactional rollback) rather than catching it, and the web adapter
 * maps it to the same 409 HideSlotUnavailable the pre-check path returns.
 */
class OverlappingSlotException(val hideId: HideId, cause: Throwable? = null) :
    RuntimeException("hide ${hideId.value} already has an overlapping active booking for the requested slot", cause)
