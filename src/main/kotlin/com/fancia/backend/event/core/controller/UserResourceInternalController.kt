package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.UserResourceCleanupService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/users")
@Hidden
class UserResourceInternalController(
    private val userResourceCleanupService: UserResourceCleanupService,
) {
    @DeleteMapping("/{userId}/resources")
    fun deleteUserResources(
        @PathVariable userId: UUID,
    ): ResponseEntity<Void> {
        userResourceCleanupService.deleteResourcesForUser(userId)
        return ResponseEntity.noContent().build()
    }
}
