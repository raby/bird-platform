package com.digitalbluebird.bookings.config

import com.digitalbluebird.bookings.adapter.outbound.persistence.JdbcBookingRepository
import com.digitalbluebird.bookings.adapter.outbound.persistence.JdbcIdempotencyKeysRepository
import com.digitalbluebird.bookings.application.BookingService
import com.digitalbluebird.bookings.domain.port.inbound.CancelBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.ConfirmBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.FindBookingUseCase
import com.digitalbluebird.bookings.domain.port.inbound.RequestBookingUseCase
import com.digitalbluebird.bookings.domain.port.outbound.BookingRepository
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

    @Bean
    fun bookingService(
        bookings: BookingRepository,
        idempotencyKeys: IdempotencyKeysRepository,
        outbox: OutboxRepository,
        clock: Clock,
        objectMapper: ObjectMapper,
    ): BookingService = BookingService(bookings, idempotencyKeys, outbox, clock, objectMapper)

    @Bean
    fun requestBookingUseCase(bookingService: BookingService): RequestBookingUseCase = bookingService

    @Bean
    fun confirmBookingUseCase(bookingService: BookingService): ConfirmBookingUseCase = bookingService

    @Bean
    fun cancelBookingUseCase(bookingService: BookingService): CancelBookingUseCase = bookingService

    @Bean
    fun findBookingUseCase(bookingService: BookingService): FindBookingUseCase = bookingService
}
