package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.common.moderation.core.dto.BlockedResourceResponse
import com.fancia.backend.shared.common.moderation.core.dto.CreateBlockedResourceRequest
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "user-service",
    path = "/api/blocked",
    configuration = [FeignConfig::class],
)
interface UserBlockedClient {
    @PostMapping
    fun block(@RequestBody request: CreateBlockedResourceRequest): BlockedResourceResponse
}
