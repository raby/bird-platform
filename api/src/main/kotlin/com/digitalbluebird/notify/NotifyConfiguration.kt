package com.digitalbluebird.notify

import com.digitalbluebird.notifications.domain.port.inbound.NotifyUseCase
import com.digitalbluebird.shared.infra.outbox.OutboxHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the booking→notification bridge at the composition root. Exposed as an [OutboxHandler] so the
 * shared relay collects it alongside the read-model projector and the visitor-permit saga.
 */
@Configuration
class NotifyConfiguration {
    @Bean
    fun bookingNotifier(notify: NotifyUseCase, objectMapper: ObjectMapper): OutboxHandler =
        BookingNotifier(notify, objectMapper)
}
