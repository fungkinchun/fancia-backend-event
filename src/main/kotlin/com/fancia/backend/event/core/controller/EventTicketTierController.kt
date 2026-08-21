package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.EventTicketTierService
import com.fancia.backend.shared.event.core.dto.CreateEventTicketTierRequest
import com.fancia.backend.shared.event.core.dto.EventTicketTierResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventTicketTierRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/events/{eventId}/ticket-tiers")
@Tag(name = "Event Ticket Tiers", description = "Priced ticket tiers for an event")
@SecurityRequirement(name = "bearerAuth")
class EventTicketTierController(
    private val eventTicketTierService: EventTicketTierService,
) {
    @Operation(summary = "List ticket tiers for an event")
    @GetMapping
    fun list(@PathVariable eventId: UUID): ResponseEntity<List<EventTicketTierResponse>> =
        ResponseEntity.ok(eventTicketTierService.list(eventId))

    @Operation(summary = "Get a ticket tier")
    @GetMapping("/{tierId}")
    fun get(
        @PathVariable eventId: UUID,
        @PathVariable tierId: UUID,
    ): ResponseEntity<EventTicketTierResponse> =
        ResponseEntity.ok(eventTicketTierService.get(eventId, tierId))

    @Operation(summary = "Create a ticket tier (host). Paid tiers require Stripe payouts ready.")
    @PostMapping
    fun create(
        @PathVariable eventId: UUID,
        @RequestBody @Valid request: CreateEventTicketTierRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<EventTicketTierResponse> =
        ResponseEntity.ok(eventTicketTierService.create(eventId, request, jwt))

    @Operation(summary = "Update a ticket tier (host)")
    @PutMapping("/{tierId}")
    fun update(
        @PathVariable eventId: UUID,
        @PathVariable tierId: UUID,
        @RequestBody @Valid request: UpdateEventTicketTierRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<EventTicketTierResponse> =
        ResponseEntity.ok(eventTicketTierService.update(eventId, tierId, request, jwt))

    @Operation(summary = "Delete a ticket tier (host)")
    @DeleteMapping("/{tierId}")
    fun delete(
        @PathVariable eventId: UUID,
        @PathVariable tierId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        eventTicketTierService.delete(eventId, tierId, jwt)
        return ResponseEntity.noContent().build()
    }
}
