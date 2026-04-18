package com.digitalbluebird.shared.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LocationTest {

    @Test
    fun `accepts valid coordinates`() {
        val loc = Location.of(52.0, 1.5)
        assertThat(loc.latitude.value).isEqualTo(52.0)
        assertThat(loc.longitude.value).isEqualTo(1.5)
    }

    @Test
    fun `rejects latitude above 90`() {
        val e = assertThrows<IllegalArgumentException> { Latitude(91.0) }
        assertThat(e).messageContains("Latitude")
    }

    @Test
    fun `rejects latitude below -90`() {
        assertThrows<IllegalArgumentException> { Latitude(-91.0) }
    }

    @Test
    fun `rejects longitude above 180`() {
        assertThrows<IllegalArgumentException> { Longitude(181.0) }
    }

    @Test
    fun `rejects longitude below -180`() {
        assertThrows<IllegalArgumentException> { Longitude(-181.0) }
    }
}
