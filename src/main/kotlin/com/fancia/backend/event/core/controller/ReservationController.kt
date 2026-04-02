package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.dto.CreateReservationRequest
import com.fancia.backend.event.core.dto.ReservationResponse
import com.fancia.backend.event.core.dto.UpdateReservationRequest
import com.fancia.backend.event.core.service.ReservationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Reservation endpoints")
@SecurityRequirement(name = "bearerAuth")
class ReservationController(
    private val reservationService: ReservationService,
) {
    @Operation(
        summary = "Create reservation",
        description = "Returns the newly created reservation"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Reservation created"),
        ]
    )
    @PostMapping
    fun createReservation(
        @RequestBody request: CreateReservationRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<ReservationResponse> {
        return ResponseEntity.ok(reservationService.create(request, jwt))
    }

    @PatchMapping("/event/{eventId}/user/{userId}")
    fun updateReservation(
        @PathVariable eventId: UUID,
        @PathVariable userId: UUID,
        @RequestBody request: UpdateReservationRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<ReservationResponse> {
        return ResponseEntity.ok(reservationService.update(eventId, userId, request, jwt))
    }
}