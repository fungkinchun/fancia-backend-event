package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.common.core.dto.TagResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(name = "common-service", path = "/api", configuration = [FeignConfig::class])
interface CommonServiceClient {
    @GetMapping("/tags")
    fun getTags(@RequestParam("search") search: Set<String>): Page<TagResponse>
}