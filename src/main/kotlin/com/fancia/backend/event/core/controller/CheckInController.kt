package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.CheckInService
import com.fancia.backend.shared.event.core.dto.CheckInRequest
import com.fancia.backend.shared.event.core.dto.CheckInResultResponse
import com.fancia.backend.shared.event.core.dto.CheckInRosterResponse
import com.fancia.backend.shared.event.core.dto.CheckInSyncRequest
import com.fancia.backend.shared.event.core.dto.CheckInSyncResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/events/{eventId}/occurrences/{occurrenceId}/check-ins")
@Tag(name = "Check-in")
@SecurityRequirement(name = "bearerAuth")
class CheckInController(
    private val checkInService: CheckInService,
) {
    @PostMapping
    fun checkIn(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @RequestBody @Valid request: CheckInRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CheckInResultResponse> =
        ResponseEntity.ok(checkInService.checkIn(eventId, occurrenceId, request, jwt))

    @GetMapping("/roster")
    fun roster(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CheckInRosterResponse> =
        ResponseEntity.ok(checkInService.roster(eventId, occurrenceId, jwt))

    @PostMapping("/sync")
    fun sync(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @RequestBody @Valid request: CheckInSyncRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CheckInSyncResponse> =
        ResponseEntity.ok(checkInService.sync(eventId, occurrenceId, request, jwt))
}
