package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.EventCommentService
import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/events/{eventId}/comments")
@Tag(name = "Event Comments", description = "Comments on events")
@SecurityRequirement(name = "bearerAuth")
class EventCommentController(
    private val eventCommentService: EventCommentService,
) {
    @Operation(
        summary = "Create comment on event",
        description = "Creates a top-level comment or reply. Caller must be an event participant.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Comment created"),
            ApiResponse(responseCode = "400", description = "Validation error"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "403", description = "Not allowed to comment on this event"),
            ApiResponse(responseCode = "404", description = "Event or parent comment not found"),
        ]
    )
    @PostMapping
    fun createComment(
        @PathVariable
        @Parameter(description = "Event id")
        eventId: UUID,
        @RequestBody @Valid request: CreateCommentRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CommentResponse> {
        val comment = eventCommentService.create(eventId, request, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    @Operation(
        summary = "List comments on event",
        description = "Paginated top-level comments for the event, newest first.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Comments returned"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "Event not found"),
        ]
    )
    @GetMapping
    fun listComments(
        @PathVariable
        @Parameter(description = "Event id")
        eventId: UUID,
        @PageableDefault(size = 20)
        pageable: Pageable,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Page<CommentResponse>> {
        return ResponseEntity.ok(eventCommentService.list(eventId, pageable, jwt))
    }
}
