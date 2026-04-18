package com.digitalbluebird.bookings.domain

import com.digitalbluebird.shared.domain.DomainError

sealed interface BookingError : DomainError {
    data class InvalidBookingId(override val message: String) : BookingError, DomainError.Validation
    data class InvalidHideId(override val message: String) : BookingError, DomainError.Validation
    data class InvalidObserverId(override val message: String) : BookingError, DomainError.Validation
    data class InvalidSlot(override val message: String) : BookingError, DomainError.Validation
    data class InvalidPartySize(override val message: String) : BookingError, DomainError.Validation
    data class InvalidIdempotencyKey(override val message: String) : BookingError, DomainError.Validation

    data class BookingNotFound(val id: BookingId) : BookingError, DomainError.NotFound {
        override val message: String = "booking not found: ${id.value}"
    }

    data class VersionConflict(val id: BookingId, val expectedVersion: Long) : BookingError, DomainError.Conflict {
        override val message: String = "optimistic lock: booking ${id.value} was modified concurrently (expected version $expectedVersion)"
    }

    data class StateTransitionNotAllowed(
        val id: BookingId,
        val current: BookingStatus,
        val requested: BookingStatus,
    ) : BookingError, DomainError.Conflict {
        override val message: String = "cannot transition booking ${id.value} from $current to $requested"
    }

    data class IdempotencyKeyConflict(val key: IdempotencyKey) : BookingError, DomainError.Conflict {
        override val message: String = "idempotency key ${key.value} already used for a different request"
    }

    data class HideSlotUnavailable(val hideId: HideId) : BookingError, DomainError.Conflict {
        override val message: String = "hide ${hideId.value} has an overlapping booking for the requested slot"
    }
}
