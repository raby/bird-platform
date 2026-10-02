package com.digitalbluebird.saga

import com.digitalbluebird.bookings.domain.port.inbound.CancelBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.FindBookingUseCase
import com.digitalbluebird.permits.domain.port.inbound.IssueVisitorPermitUseCase
import com.digitalbluebird.shared.infra.outbox.OutboxHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the cross-context sagas. They belong at the composition root, where depending on several
 * contexts' inbound ports is expected. The saga is exposed as an [OutboxHandler] so the shared relay
 * collects it alongside the read-model projectors.
 */
@Configuration
class SagaConfiguration {

    @Bean
    fun visitorPermitSaga(
        issueVisitorPermit: IssueVisitorPermitUseCase,
        findBooking: FindBookingUseCase,
        cancelBooking: CancelBookingUseCase,
        objectMapper: ObjectMapper,
    ): OutboxHandler = VisitorPermitSaga(issueVisitorPermit, findBooking, cancelBooking, objectMapper)
}
