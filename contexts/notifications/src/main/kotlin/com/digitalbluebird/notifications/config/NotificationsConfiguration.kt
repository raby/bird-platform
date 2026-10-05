package com.digitalbluebird.notifications.config

import com.digitalbluebird.notifications.adapter.inbound.schedule.NotificationDispatchPoller
import com.digitalbluebird.notifications.adapter.outbound.channel.LoggingNotificationChannel
import com.digitalbluebird.notifications.adapter.outbound.persistence.JdbcNotificationRepository
import com.digitalbluebird.notifications.application.NotificationDispatcher
import com.digitalbluebird.notifications.application.NotificationService
import com.digitalbluebird.notifications.domain.port.inbound.DispatchNotificationsUseCase
import com.digitalbluebird.notifications.domain.port.outbound.NotificationChannel
import com.digitalbluebird.notifications.domain.port.outbound.NotificationRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock

@Configuration
class NotificationsConfiguration {

    @Bean
    fun notificationRepository(jdbc: NamedParameterJdbcTemplate): NotificationRepository =
        JdbcNotificationRepository(jdbc)

    @Bean
    fun notificationChannel(): NotificationChannel = LoggingNotificationChannel()

    // One concrete bean behind both NotifyUseCase and ViewNotificationsUseCase (the api
    // BookingNotifier injects the former, the controller the latter). One bean per port keeps the
    // injections unambiguous — do not also register interface-typed beans for the same instance.
    @Bean
    fun notificationService(notifications: NotificationRepository, clock: Clock): NotificationService =
        NotificationService(notifications, clock)

    @Bean
    fun notificationDispatcher(
        notifications: NotificationRepository,
        channel: NotificationChannel,
        clock: Clock,
    ): NotificationDispatcher = NotificationDispatcher(notifications, channel, clock)

    @Bean
    fun notificationDispatchPoller(dispatcher: DispatchNotificationsUseCase): NotificationDispatchPoller =
        NotificationDispatchPoller(dispatcher)
}
