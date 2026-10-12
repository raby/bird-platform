package com.digitalbluebird.expedition

import com.digitalbluebird.bookings.domain.port.inbound.CancelBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.ConfirmBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.FindBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingUseCase
import com.digitalbluebird.expeditions.domain.port.inbound.HandleBookingCancelledUseCase
import com.digitalbluebird.expeditions.domain.port.outbound.HideBookingGateway
import com.digitalbluebird.shared.infra.outbox.OutboxHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the expedition process manager at the composition root: the [HideBookingGateway] adapter onto
 * the bookings context (consumed by the expeditions service), and the BookingCancelled→compensate bridge
 * exposed as an [OutboxHandler] so the shared relay drives it.
 */
@Configuration
class ExpeditionWiring {

    @Bean
    fun hideBookingGateway(
        requestBooking: RequestBookingUseCase,
        confirmBooking: ConfirmBookingUseCase,
        cancelBooking: CancelBookingUseCase,
        findBooking: FindBookingUseCase,
    ): HideBookingGateway = ExpeditionBookingGateway(requestBooking, confirmBooking, cancelBooking, findBooking)

    @Bean
    fun expeditionProcessManager(
        handleBookingCancelled: HandleBookingCancelledUseCase,
        objectMapper: ObjectMapper,
    ): OutboxHandler = ExpeditionProcessManager(handleBookingCancelled, objectMapper)
}
