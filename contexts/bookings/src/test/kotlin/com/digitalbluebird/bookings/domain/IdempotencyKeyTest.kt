package com.digitalbluebird.bookings.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IdempotencyKeyTest {

    @Test
    fun `accepts alphanumeric hyphen underscore within length bounds`() {
        val key = IdempotencyKey("abc-def_1234")
        assertThat(key.value).isEqualTo("abc-def_1234")
    }

    @Test
    fun `rejects too short`() {
        val err = assertThrows<IllegalArgumentException> { IdempotencyKey("short") }
        assertThat(err).messageContains("length")
    }

    @Test
    fun `rejects too long`() {
        val err = assertThrows<IllegalArgumentException> { IdempotencyKey("a".repeat(129)) }
        assertThat(err).messageContains("length")
    }

    @Test
    fun `rejects illegal characters`() {
        val err = assertThrows<IllegalArgumentException> { IdempotencyKey("has space!!!") }
        assertThat(err).messageContains("letters")
    }
}
