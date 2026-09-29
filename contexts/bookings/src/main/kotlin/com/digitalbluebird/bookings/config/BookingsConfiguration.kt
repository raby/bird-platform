package com.digitalbluebird.bookings.config

import com.digitalbluebird.bookings.adapter.outbound.persistence.JdbcBookingRepository
import com.digitalbluebird.bookings.adapter.outbound.persistence.JdbcHideAvailabilityReadModel
import com.digitalbluebird.bookings.adapter.outbound.persistence.JdbcIdempotencyKeysRepository
import com.digitalbluebird.bookings.application.BookingService
import com.digitalbluebird.bookings.application.HideAvailabilityProjector
import com.digitalbluebird.bookings.application.HideAvailabilityQueryService
import com.digitalbluebird.bookings.domain.port.outbound.BookingRepository
import com.digitalbluebird.bookings.domain.port.outbound.HideAvailabilityReadModel
import com.digitalbluebird.bookings.domain.port.outbound.IdempotencyKeysRepository
import com.digitalbluebird.shared.infra.outbox.OutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock

@Configuration
class BookingsConfiguration {

    @Bean
    fun bookingRepository(jdbc: NamedParameterJdbcTemplate): BookingRepository =
        JdbcBookingRepository(jdbc)

    @Bean
    fun idempotencyKeysRepository(jdbc: NamedParameterJdbcTemplate): IdempotencyKeysRepository =
        JdbcIdempotencyKeysRepository(jdbc)

    // BookingController injects the inbound ports (RequestBookingUseCase, ConfirmBookingUseCase, …);
    // each resolves to this single concrete bean. Do NOT also register interface-typed beans for the
    // same instance — two beans satisfying one port make the injection ambiguous
    // (NoUniqueBeanDefinitionException at context startup).
    @Bean
    fun bookingService(
        bookings: BookingRepository,
        idempotencyKeys: IdempotencyKeysRepository,
        outbox: OutboxRepository,
        clock: Clock,
        objectMapper: ObjectMapper,
    ): BookingService = BookingService(bookings, idempotencyKeys, outbox, clock, objectMapper)

    @Bean
    fun hideAvailabilityReadModel(jdbc: NamedParameterJdbcTemplate): HideAvailabilityReadModel =
        JdbcHideAvailabilityReadModel(jdbc)

    // Registered as an OutboxHandler bean; the shared OutboxRelay collects it and drives the
    // hide-availability projection off BookingConfirmed / BookingCancelled events.
    @Bean
    fun hideAvailabilityProjector(
        readModel: HideAvailabilityReadModel,
        objectMapper: ObjectMapper,
    ): HideAvailabilityProjector = HideAvailabilityProjector(readModel, objectMapper)

    // Injected via the ViewHideAvailabilityUseCase port; single concrete bean, per the note above.
    @Bean
    fun hideAvailabilityQueryService(readModel: HideAvailabilityReadModel): HideAvailabilityQueryService =
        HideAvailabilityQueryService(readModel)
}
