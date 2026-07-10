package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.EventParticipantService
import com.fancia.backend.shared.event.core.dto.EventParticipantResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/events")
@Tag(name = "Event Participants", description = "Event participant endpoints")
@SecurityRequirement(name = "bearerAuth")
class EventParticipantController(
    private val eventParticipantService: EventParticipantService,
) {
    @GetMapping("/{eventId}/occurrences/{occurrenceId}/participants")
    @Operation(
        summary = "List occurrence participants",
        description = "Returns a paginated list of participants for a specific occurrence",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "List of occurrence participants returned"),
        ],
    )
    fun listOccurrenceParticipants(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PageableDefault(size = 20)
        pageable: Pageable,
    ): ResponseEntity<Page<EventParticipantResponse>> {
        val participants = eventParticipantService.findParticipants(eventId, occurrenceId, pageable)
        return ResponseEntity.ok(participants)
    }
}
