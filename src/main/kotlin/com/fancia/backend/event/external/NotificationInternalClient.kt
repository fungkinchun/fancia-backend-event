package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.notification.core.dto.SendPushNotificationRequest
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "notification-internal-service",
    path = "/internal",
    configuration = [FeignConfig::class],
)
interface NotificationInternalClient {
    @PostMapping("/push")
    fun sendPush(@RequestBody request: SendPushNotificationRequest)
}
