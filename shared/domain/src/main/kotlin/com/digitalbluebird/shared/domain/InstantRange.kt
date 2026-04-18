package com.digitalbluebird.shared.domain

import java.time.Duration
import java.time.Instant

data class InstantRange(
    val start: Instant,
    val endExclusive: Instant,
) {
    init {
        require(start.isBefore(endExclusive)) {
            "start ($start) must be before endExclusive ($endExclusive)"
        }
    }

    val duration: Duration get() = Duration.between(start, endExclusive)

    operator fun contains(instant: Instant): Boolean =
        !instant.isBefore(start) && instant.isBefore(endExclusive)

    fun overlaps(other: InstantRange): Boolean =
        start.isBefore(other.endExclusive) && other.start.isBefore(endExclusive)
}
