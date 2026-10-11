package com.digitalbluebird.shared.infra.resilience

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CircuitBreakerTest {

    // A clock the test advances by hand, so the reset window is exercised without sleeping.
    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    private val clock = MutableClock(Instant.parse("2026-05-01T00:00:00Z"))
    private fun breaker(threshold: Int = 2, reset: Duration = Duration.ofSeconds(30)) =
        CircuitBreaker(threshold, reset, clock, "test")

    private fun fail(): Nothing = throw RuntimeException("boom")

    @Test
    fun `stays closed and passes results through while calls succeed`() {
        val cb = breaker()
        assertThat(cb.execute { 42 }).isEqualTo(42)
        assertThat(cb.currentState).isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun `opens after the failure threshold, then fails fast without calling the block`() {
        val cb = breaker(threshold = 2)
        repeat(2) { runCatching { cb.execute { fail() } } }
        assertThat(cb.currentState).isEqualTo(CircuitBreaker.State.OPEN)

        var called = false
        assertThrows<CircuitOpenException> { cb.execute { called = true } }
        assertThat(called).isFalse() // short-circuited: the block never ran
    }

    @Test
    fun `a success resets the consecutive-failure count`() {
        val cb = breaker(threshold = 2)
        runCatching { cb.execute { fail() } }
        cb.execute { "ok" } // resets the count
        runCatching { cb.execute { fail() } }
        assertThat(cb.currentState).isEqualTo(CircuitBreaker.State.CLOSED) // only one failure since the reset
    }

    @Test
    fun `after the reset timeout it half-opens, and a successful trial closes it`() {
        val cb = breaker(threshold = 1, reset = Duration.ofSeconds(30))
        runCatching { cb.execute { fail() } }
        assertThat(cb.currentState).isEqualTo(CircuitBreaker.State.OPEN)

        clock.now = clock.now.plusSeconds(31)
        assertThat(cb.currentState).isEqualTo(CircuitBreaker.State.HALF_OPEN)

        assertThat(cb.execute { "recovered" }).isEqualTo("recovered")
        assertThat(cb.currentState).isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun `a failed half-open trial reopens immediately`() {
        val cb = breaker(threshold = 1, reset = Duration.ofSeconds(30))
        runCatching { cb.execute { fail() } }
        clock.now = clock.now.plusSeconds(31)
        assertThat(cb.currentState).isEqualTo(CircuitBreaker.State.HALF_OPEN)

        runCatching { cb.execute { fail() } }
        assertThat(cb.currentState).isEqualTo(CircuitBreaker.State.OPEN)
    }

    @Test
    fun `an Error in a half-open trial releases the trial slot instead of wedging the breaker`() {
        val cb = breaker(threshold = 1, reset = Duration.ofSeconds(30))
        runCatching { cb.execute { fail() } } // open
        clock.now = clock.now.plusSeconds(31) // -> half-open

        // The trial throws an Error, not an Exception. The breaker must still release the trial slot.
        runCatching { cb.execute { throw StackOverflowError("boom") } }

        // Not wedged: after the next reset window a trial is allowed again, and a success closes it.
        clock.now = clock.now.plusSeconds(31)
        assertThat(cb.execute { "recovered" }).isEqualTo("recovered")
        assertThat(cb.currentState).isEqualTo(CircuitBreaker.State.CLOSED)
    }
}
