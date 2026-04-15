package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.user.core.dto.UserResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.util.*

@FeignClient(name = "user-service", url = "/api", configuration = [FeignConfig::class])
interface UserServiceClient {
    @GetMapping("/users/{id}")
    fun getUserById(@PathVariable("id") id: UUID): UserResponse

    @GetMapping("/users/email/{email}")
    fun getUserByEmail(@PathVariable("email") email: String): UserResponse
}