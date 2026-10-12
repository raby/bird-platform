package com.digitalbluebird.reserves.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.digitalbluebird.reserves.domain.NearbyReserve
import com.digitalbluebird.reserves.domain.Reserve
import com.digitalbluebird.reserves.domain.ReserveError
import com.digitalbluebird.reserves.domain.ReserveId
import com.digitalbluebird.reserves.domain.port.outbound.ReserveRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class ReserveServiceTest {

    private val repo = mockk<ReserveRepository>(relaxed = true)
    private val service = ReserveService(repo)

    @Test
    fun `nearby delegates to the repository for valid coordinates`() {
        val nearby = NearbyReserve(Reserve(ReserveId(UUID.randomUUID()), "Ham Wall", "Somerset", 51.15, -2.78), 1.2)
        every { repo.findNearby(51.15, -2.78, 50.0) } returns listOf(nearby)

        assertThat(service.nearby(51.15, -2.78, 50.0).getOrNull()).isEqualTo(listOf(nearby))
    }

    @Test
    fun `nearby rejects out-of-range coordinates without touching the repository`() {
        assertThat(service.nearby(91.0, 0.0, 50.0).leftOrNull()!!).isInstanceOf(ReserveError.InvalidCoordinates::class)
        assertThat(service.nearby(0.0, 181.0, 50.0).leftOrNull()!!).isInstanceOf(ReserveError.InvalidCoordinates::class)
        verify(exactly = 0) { repo.findNearby(any(), any(), any()) }
    }

    @Test
    fun `nearby rejects a non-positive radius`() {
        assertThat(service.nearby(51.0, -2.0, 0.0).leftOrNull()!!).isInstanceOf(ReserveError.InvalidRadius::class)
    }

    @Test
    fun `byId returns the reserve when the repository has it`() {
        val id = UUID.randomUUID()
        val reserve = Reserve(ReserveId(id), "Ham Wall", "Somerset", 51.15, -2.78)
        every { repo.findById(ReserveId(id)) } returns reserve
        assertThat(service.byId(id.toString()).getOrNull()).isEqualTo(reserve)
    }

    @Test
    fun `byId rejects a non-uuid id`() {
        assertThat(service.byId("not-a-uuid").leftOrNull()!!).isInstanceOf(ReserveError.InvalidReserveId::class)
    }

    @Test
    fun `byId reports not found when the repository has none`() {
        val id = UUID.randomUUID()
        every { repo.findById(ReserveId(id)) } returns null
        assertThat(service.byId(id.toString()).leftOrNull()!!).isInstanceOf(ReserveError.ReserveNotFound::class)
    }
}
