package com.digitalbluebird.shared.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

class InstantRangeTest {

    private val t0 = Instant.parse("2026-04-18T05:00:00Z")
    private val t1 = t0.plus(Duration.ofHours(2))
    private val t2 = t1.plus(Duration.ofHours(2))

    @Test
    fun `reports duration`() {
        assertThat(InstantRange(t0, t1).duration).isEqualTo(Duration.ofHours(2))
    }

    @Test
    fun `contains instant within range but not at endExclusive`() {
        val range = InstantRange(t0, t1)
        assertThat(t0 in range).isTrue()
        assertThat(t0.plus(Duration.ofMinutes(30)) in range).isTrue()
        assertThat(t1 in range).isFalse()
    }

    @Test
    fun `overlaps adjacent-but-not-touching returns false`() {
        val a = InstantRange(t0, t1)
        val b = InstantRange(t1, t2)
        assertThat(a.overlaps(b)).isFalse()
    }

    @Test
    fun `overlaps when ranges share an instant`() {
        val a = InstantRange(t0, t1.plus(Duration.ofMinutes(30)))
        val b = InstantRange(t1, t2)
        assertThat(a.overlaps(b)).isTrue()
    }

    @Test
    fun `rejects end before start`() {
        assertThrows<IllegalArgumentException> { InstantRange(t1, t0) }
    }

    @Test
    fun `rejects empty range`() {
        assertThrows<IllegalArgumentException> { InstantRange(t0, t0) }
    }
}
