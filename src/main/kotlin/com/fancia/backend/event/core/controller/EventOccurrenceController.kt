package com.fancia.backend.event.core.controller

import com.fancia.backend.shared.event.core.dto.EventOccurrenceResponse
import com.fancia.backend.event.core.service.EventOccurrenceService
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import com.fancia.backend.event.core.repository.EventRepository
import java.util.UUID

@RestController
@RequestMapping("/api/events")
@Tag(name = "Event Occurrences", description = "Event occurrence endpoints")
@SecurityRequirement(name = "bearerAuth")
class EventOccurrenceController(
    private val eventRepository: EventRepository,
    private val eventOccurrenceService: EventOccurrenceService,
) {
    @GetMapping("/{eventId}/occurrences")
    @Operation(summary = "List event occurrences")
    fun listOccurrences(
        @PathVariable eventId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<EventOccurrenceResponse>> {
        eventRepository.findByIdOrNull(eventId) ?: throw EventNotFoundException(eventId)
        return ResponseEntity.ok(eventOccurrenceService.findByEventId(eventId, pageable))
    }
}
