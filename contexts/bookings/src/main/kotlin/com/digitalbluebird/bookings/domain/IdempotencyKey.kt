package com.digitalbluebird.bookings.domain

@JvmInline
value class IdempotencyKey(val value: String) {
    init {
        require(value.length in MIN_LENGTH..MAX_LENGTH) {
            "idempotency key length must be between $MIN_LENGTH and $MAX_LENGTH (was ${value.length})"
        }
        require(value.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "idempotency key must contain only letters, digits, hyphens or underscores"
        }
    }

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 128
    }
}
