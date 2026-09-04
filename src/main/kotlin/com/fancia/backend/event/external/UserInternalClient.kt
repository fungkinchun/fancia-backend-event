package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.common.moderation.core.dto.BlockedResourcesGroupedResponse
import com.fancia.backend.shared.common.moderation.core.enums.BlockedResourceType
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

@FeignClient(
    name = "user-internal-service",
    path = "/internal",
    configuration = [FeignConfig::class],
)
interface UserInternalClient {
    @GetMapping("/users/{id}/blocked")
    fun getBlocked(
        @PathVariable id: UUID,
        @RequestParam(required = false) types: List<BlockedResourceType>?,
    ): BlockedResourcesGroupedResponse
}
