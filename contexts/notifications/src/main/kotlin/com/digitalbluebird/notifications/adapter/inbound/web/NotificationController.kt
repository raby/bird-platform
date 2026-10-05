package com.digitalbluebird.notifications.adapter.inbound.web

import com.digitalbluebird.notifications.domain.port.inbound.ViewNotificationsUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Read side of the notifications context: the recent notifications, so the inbox is observable. */
@RestController
@RequestMapping("/notifications")
class NotificationController(
    private val viewNotifications: ViewNotificationsUseCase,
) {
    @GetMapping
    fun recent(@RequestParam(defaultValue = "50") limit: Int): List<NotificationResponse> =
        viewNotifications.recent(limit.coerceIn(1, 200)).map(NotificationResponse::from)
}
