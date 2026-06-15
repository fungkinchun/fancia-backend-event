package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.EventService
import com.fancia.backend.shared.event.core.dto.CreateEventRequest
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventRequest
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
        @RequestBody @Valid request: CreateEventRequest,
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

    @GetMapping("/{id}")
    @Operation(
        summary = "Get event by id",
        description = "Returns an event by id. Private and group events are accessible via direct link."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Event returned"),
            ApiResponse(responseCode = "404", description = "Event not found"),
        ]
    )
    fun getEvent(@PathVariable id: UUID): ResponseEntity<EventResponse> {
        return ResponseEntity.ok(eventService.findById(id))
    }

    @GetMapping
    @Operation(
        summary = "List events",
        description = "Returns a paginated list of discoverable events. Public events are listed globally; group events appear when filtered by interest group. Private events are excluded and only accessible via direct link. Supports proximity search when lat/lng are provided."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "List of events returned"),
        ]
    )
    fun listEvents(
        @RequestParam(required = false)
        @Parameter(description = "Fuzzy search term for event name")
        name: String?,
        @Parameter(description = "Fuzzy search term for event description")
        description: String?,
        @RequestParam(required = false)
        @Parameter(description = "Fuzzy search term for tags, use comma to separate multiple tags")
        tags: String? = null,
        @RequestParam(required = false, name = "interestGroup")
        @Parameter(description = "Filter events linked to this interest group")
        interestGroup: UUID? = null,
        @RequestParam(required = false)
        @Parameter(description = "Latitude for proximity search")
        lat: Double?,
        @RequestParam(required = false)
        @Parameter(description = "Longitude for proximity search")
        lng: Double?,
        @RequestParam(required = false)
        @Parameter(description = "Search radius in kilometres (required with lat/lng for proximity search)")
        radiusKm: Double? = null,
        @PageableDefault(size = 20)
        pageable: Pageable
    ): ResponseEntity<Page<EventResponse>> {
        val groups = eventService.findAll(name, description, tags, interestGroup, lat, lng, radiusKm, pageable)
        return ResponseEntity.ok(groups)
    }
}