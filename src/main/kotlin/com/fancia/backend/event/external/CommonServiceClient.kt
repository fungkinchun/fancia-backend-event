package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.common.core.dto.TagsResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(name = "common-service", path = "/api", configuration = [FeignConfig::class])
interface CommonServiceClient {
    @GetMapping("/tags")
    fun getTags(@RequestParam("search") search: Set<String>): TagsResponse
}