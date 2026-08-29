package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.user.core.dto.UserResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.util.UUID

@FeignClient(
    name = "user-internal-service",
    path = "/internal",
    configuration = [FeignConfig::class],
)
interface UserInternalClient {
    @GetMapping("/users/{id}")
    fun getUser(@PathVariable id: UUID): UserResponse
}
