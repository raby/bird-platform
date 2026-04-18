package com.digitalbluebird.bookings.domain

enum class BookingStatus {
    REQUESTED,
    CONFIRMED,
    CANCELLED;

    val isTerminal: Boolean get() = this == CANCELLED

    fun canTransitionTo(next: BookingStatus): Boolean = when (this) {
        REQUESTED -> next == CONFIRMED || next == CANCELLED
        CONFIRMED -> next == CANCELLED
        CANCELLED -> false
    }
}
