package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.user.core.dto.UpdateUserRequest
import com.fancia.backend.shared.user.core.dto.UserResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "user-service",
    path = "/api/users",
    configuration = [FeignConfig::class],
)
interface UserServiceClient {
    @GetMapping("/me")
    fun getCurrentUser(): UserResponse

    @PutMapping
    fun updateUser(@RequestBody request: UpdateUserRequest): UserResponse
}
