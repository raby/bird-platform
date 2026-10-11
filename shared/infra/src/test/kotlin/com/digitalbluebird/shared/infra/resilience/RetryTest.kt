package com.digitalbluebird.shared.infra.resilience

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class RetryTest {

    @Test
    fun `returns the first successful result without further attempts`() {
        var calls = 0
        val result = retry(maxAttempts = 3) { calls++; "ok" }
        assertThat(result).isEqualTo("ok")
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `retries up to maxAttempts then re-throws the last exception`() {
        var calls = 0
        val slept = mutableListOf<Duration>()
        val e = assertThrows<RuntimeException> {
            retry(maxAttempts = 3, delay = Duration.ofMillis(10), sleep = { slept += it }) {
                calls++; throw RuntimeException("attempt $calls")
            }
        }
        assertThat(calls).isEqualTo(3)
        assertThat(slept.size).isEqualTo(2) // slept between the three attempts, not after the last
        assertThat(e.message).isEqualTo("attempt 3")
    }

    @Test
    fun `eventually succeeds within the attempt budget`() {
        var calls = 0
        val result = retry(maxAttempts = 3, delay = Duration.ofMillis(1), sleep = {}) {
            calls++
            if (calls < 3) throw RuntimeException("transient")
            "recovered"
        }
        assertThat(result).isEqualTo("recovered")
        assertThat(calls).isEqualTo(3)
    }

    @Test
    fun `does not retry an exception shouldRetry rejects`() {
        var calls = 0
        assertThrows<IllegalArgumentException> {
            retry(maxAttempts = 5, shouldRetry = { it !is IllegalArgumentException }) {
                calls++; throw IllegalArgumentException("nope")
            }
        }
        assertThat(calls).isEqualTo(1)
    }
}
