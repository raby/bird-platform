package com.digitalbluebird.integrations.ebird.adapter.outbound.ebird

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.digitalbluebird.integrations.ebird.domain.port.outbound.SightingToSync
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class StubEbirdGatewayTest {

    private val gateway = StubEbirdGateway()

    @Test
    fun `the anti-corruption layer maps our model to eBird's checklist shape`() {
        val checklist = gateway.translate(
            SightingToSync(
                sightingId = "sighting-1",
                speciesId = "turdus-merula",
                latitude = 54.12345678,
                longitude = -2.8,
                observedAt = Instant.parse("2026-05-02T08:30:00Z"),
                count = 3,
            ),
        )
        assertThat(checklist.speciesCode).isEqualTo("turdusme") // "turdus-merula" → stripped, truncated
        assertThat(checklist.latitude).isEqualTo("54.12346") // formatted to 5 dp
        assertThat(checklist.obsDt).isEqualTo("2026-05-02 08:30") // eBird's date format
        assertThat(checklist.howMany).isEqualTo(3)
        assertThat(checklist.observationId).isEqualTo("sighting-1")
    }

    @Test
    fun `submitting a normal sighting is accepted`() {
        gateway.submit(
            SightingToSync(UUID.randomUUID().toString(), "turdus-merula", 54.1, -2.8, Instant.now(), 1),
        ) // no exception == accepted
    }

    @Test
    fun `submitting an unreachable species fails, so the breaker can observe it`() {
        assertThrows<EbirdUnavailableException> {
            gateway.submit(
                SightingToSync(UUID.randomUUID().toString(), "unreachable-sp", 54.1, -2.8, Instant.now(), 1),
            )
        }
    }
}
