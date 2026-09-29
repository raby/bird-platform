package com.digitalbluebird.bookings.application

import com.digitalbluebird.bookings.domain.Hide
import com.digitalbluebird.bookings.domain.HideAvailability
import com.digitalbluebird.bookings.domain.HideId
import com.digitalbluebird.bookings.domain.port.inbound.ViewHideAvailabilityUseCase
import com.digitalbluebird.bookings.domain.port.outbound.HideAvailabilityReadModel
import com.digitalbluebird.shared.domain.InstantRange
import org.springframework.transaction.annotation.Transactional

/** Serves hide-availability queries from the read model. Read-only, so declared `open` for the proxy. */
open class HideAvailabilityQueryService(
    private val readModel: HideAvailabilityReadModel,
) : ViewHideAvailabilityUseCase {

    @Transactional(readOnly = true)
    override fun listHides(): List<Hide> = readModel.listHides()

    @Transactional(readOnly = true)
    override fun availability(hideId: HideId, slot: InstantRange): HideAvailability? =
        readModel.availabilityFor(hideId, slot)
}
