package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.dto.CreateEventRequest
import com.fancia.backend.event.core.dto.EventResponse
import com.fancia.backend.event.core.dto.UpdateEventRequest
import com.fancia.backend.event.core.service.EventService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Event endpoints")
@SecurityRequirement(name = "bearerAuth")
class EventController(
    private val eventService: EventService,
) {
    @Operation(
        summary = "Create event",
        description = "Returns the newly created event"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Event created"),
        ]
    )
    @PostMapping
    fun createEvent(
        @RequestBody request: CreateEventRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<EventResponse> {
        val event = eventService.create(request, jwt)
        return ResponseEntity.ok(event)
    }

    @PutMapping("/{id}")
    fun updateEvent(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdateEventRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<EventResponse> {
        return ResponseEntity.ok(eventService.update(id, request, jwt))
    }

    @GetMapping
    @Operation(
        summary = "List events",
        description = "Returns a paginated list of events. Supports fuzzy search by name, description and tags."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "List of events returned"),
        ]
    )
    fun listInterestGroups(
        @RequestParam(required = false)
        @Parameter(description = "Fuzzy search term for event name")
        name: String?,
        @Parameter(description = "Fuzzy search term for event description")
        description: String?,
        @RequestParam(required = false)
        @Parameter(description = "Fuzzy search term for tags, use comma to separate multiple tags")
        tags: String? = null,
        @PageableDefault(size = 20)
        pageable: Pageable
    ): ResponseEntity<Page<EventResponse>> {
        val groups = eventService.findAll(name, description, tags, pageable)
        return ResponseEntity.ok(groups)
    }
}