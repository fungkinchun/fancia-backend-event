package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.EventService
import com.fancia.backend.event.core.service.SavedResourceService
import com.fancia.backend.shared.common.saved.core.dto.SavedResourceResponse
import com.fancia.backend.shared.event.core.dto.CreateEventRequest
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventRequest
import com.fancia.backend.shared.event.core.enums.EventType
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
import org.springframework.http.HttpStatus
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
    private val savedResourceService: SavedResourceService,
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

    @GetMapping("/saved")
    @Operation(summary = "List events saved by the current user")
    fun listSaved(
        @AuthenticationPrincipal jwt: Jwt,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<EventResponse>> =
        ResponseEntity.ok(eventService.listSavedEvents(jwt, pageable))

    @PostMapping("/{id}/saved")
    @Operation(summary = "Save an event")
    fun saveEvent(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<SavedResourceResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(savedResourceService.save(id, jwt))

    @DeleteMapping("/{id}/saved")
    @Operation(summary = "Unsave an event")
    fun unsaveEvent(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        savedResourceService.unsave(id, jwt)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{ref}")
    @Operation(
        summary = "Get event by id or slug",
        description = "Returns an event by UUID or slug. Private and group events are accessible via direct link."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Event returned"),
            ApiResponse(responseCode = "404", description = "Event not found"),
        ]
    )
    fun getEvent(
        @PathVariable ref: String,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<EventResponse> {
        return ResponseEntity.ok(eventService.findByIdOrSlug(ref, jwt))
    }

    @GetMapping
    @Operation(
        summary = "List events",
        description = "Returns a paginated list of discoverable events. Public events are listed globally; group events appear when filtered by interest group. Private events are excluded and only accessible via direct link. Supports proximity search when lat/lng are provided. With match=true or schedule=true, returns upcoming events ranked by interest relevance (exact and similar tags), blacklist exclusion, location, and schedule fit. With past=true, returns events that have already started (including recurring series with a past occurrence); those series still appear in the default upcoming list when they have future occurrences.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "List of events returned"),
        ],
    )
    fun listEvents(
        @RequestParam(required = false)
        @Parameter(description = "Fuzzy search term for event name")
        name: String?,
        @Parameter(description = "Fuzzy search term for event description")
        description: String?,
        @RequestParam(required = false)
        @Parameter(description = "Filter by tag ids (entities matching any of the ids)")
        tagIds: List<UUID> = emptyList(),
        @RequestParam(required = false, name = "interestGroup")
        @Parameter(description = "Filter events linked to this interest group")
        interestGroup: UUID? = null,
        @RequestParam(required = false)
        @Parameter(description = "Filter by event type (REGULAR or SPONTANEOUS)")
        eventType: EventType? = null,
        @RequestParam(required = false)
        @Parameter(description = "Latitude for proximity search")
        lat: Double?,
        @RequestParam(required = false)
        @Parameter(description = "Longitude for proximity search")
        lng: Double?,
        @RequestParam(required = false)
        @Parameter(description = "Search radius in kilometres for proximity search")
        radiusKm: Double = 5.0,
        @RequestParam(required = false)
        @Parameter(description = "Location label for schedule-based matching when lat/lng are not provided")
        locationLabel: String?,
        @RequestParam(name = "match", defaultValue = "false")
        @Parameter(description = "When true, match upcoming events by the supplied tagIds")
        match: Boolean,
        @RequestParam(name = "schedule", defaultValue = "false")
        @Parameter(description = "When true, match nearby events that fit the authenticated user's free schedule")
        schedule: Boolean,
        @RequestParam(required = false)
        @Parameter(description = "List events the user hosts or attends (includes past). Hidden unless privacy.showEvents is explicitly true, except when the viewer is that user.")
        userId: UUID?,
        @RequestParam(name = "past", defaultValue = "false")
        @Parameter(description = "When true, list discoverable events that have already started (one-time finished or recurring series with at least one past occurrence). Recurring series with future occurrences also appear in the default upcoming list. Ignored when userId, match, or schedule is set.")
        past: Boolean,
        @AuthenticationPrincipal jwt: Jwt?,
        @PageableDefault(size = 20)
        pageable: Pageable,
    ): ResponseEntity<Page<EventResponse>> {
        return ResponseEntity.ok(
            eventService.findAll(
                name,
                description,
                tagIds,
                interestGroup,
                eventType,
                lat,
                lng,
                radiusKm,
                locationLabel,
                match,
                schedule,
                userId,
                past,
                jwt,
                pageable,
            ),
        )
    }
}