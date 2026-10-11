package com.digitalbluebird.integration

import com.digitalbluebird.integrations.ebird.domain.port.inbound.SyncSightingUseCase
import com.digitalbluebird.shared.infra.outbox.OutboxHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the external-integration bridges at the composition root. The eBird sync is exposed as an
 * [OutboxHandler] so the shared relay drives it off SightingCreated alongside the projector, saga and
 * notifier.
 */
@Configuration
class IntegrationConfiguration {
    @Bean
    fun ebirdSightingSync(sync: SyncSightingUseCase, objectMapper: ObjectMapper): OutboxHandler =
        EbirdSightingSync(sync, objectMapper)
}
