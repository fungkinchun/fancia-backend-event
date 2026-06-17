package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.common.tag.core.dto.TagResponse
import com.fancia.backend.shared.common.tag.core.enums.TagType
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.util.*

@FeignClient(name = "common-service", path = "/api", configuration = [FeignConfig::class])
interface CommonServiceClient {
    @GetMapping("/tags")
    fun getTags(
        @RequestParam("search") search: Set<String>,
        @RequestParam("type") type: TagType,
    ): Page<TagResponse>

    @GetMapping("/tags/ids")
    fun getTagsByIds(@RequestParam("id") ids: Set<UUID>): List<TagResponse>
}
