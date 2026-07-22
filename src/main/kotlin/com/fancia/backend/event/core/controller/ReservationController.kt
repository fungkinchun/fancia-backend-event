package com.fancia.backend.event.core.controller

import com.fancia.backend.shared.event.core.dto.ReservationResponse
import com.fancia.backend.event.core.service.ReservationService
import com.fancia.backend.shared.event.core.dto.CreateReservationRequest
import com.fancia.backend.shared.event.core.dto.UpdateReservationRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/events")
@Tag(name = "Reservations", description = "Reservation endpoints")
@SecurityRequirement(name = "bearerAuth")
class ReservationController(
    private val reservationService: ReservationService,
) {
    @Operation(
        summary = "Get reservation",
        description = "Returns a reservation for a specific occurrence and user",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Reservation found"),
        ],
    )
    @GetMapping("/{eventId}/occurrences/{occurrenceId}/users/{userId}/reservations")
    fun getReservation(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PathVariable userId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ReservationResponse> {
        return ResponseEntity.ok(reservationService.get(eventId, occurrenceId, userId, jwt))
    }

    @Operation(
        summary = "Create reservation",
        description = "Returns the newly created reservation for a specific occurrence",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Reservation created"),
        ],
    )
    @PostMapping("/{eventId}/occurrences/{occurrenceId}/reservations")
    fun createReservation(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @RequestBody @Valid request: CreateReservationRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ReservationResponse> {
        return ResponseEntity.ok(reservationService.create(eventId, occurrenceId, request, jwt))
    }

    @PatchMapping("/{eventId}/occurrences/{occurrenceId}/users/{userId}/reservations")
    fun updateReservation(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PathVariable userId: UUID,
        @RequestBody @Valid request: UpdateReservationRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ReservationResponse> {
        return ResponseEntity.ok(reservationService.update(eventId, occurrenceId, userId, request, jwt))
    }
}
