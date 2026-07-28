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
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/events/{eventId}/occurrences/{occurrenceId}/comments")
@Tag(name = "Event Occurrence Comments", description = "Comments on event occurrences")
@SecurityRequirement(name = "bearerAuth")
class EventCommentController(
    private val eventCommentService: EventCommentService,
) {
    @Operation(
        summary = "Create comment on occurrence",
        description = "Creates a top-level comment or reply. Caller must be an occurrence participant.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Comment created"),
            ApiResponse(responseCode = "400", description = "Validation error"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "403", description = "Not allowed to comment on this occurrence"),
            ApiResponse(responseCode = "404", description = "Event, occurrence, or parent comment not found"),
        ],
    )
    @PostMapping
    fun createComment(
        @PathVariable
        @Parameter(description = "Event id")
        eventId: UUID,
        @PathVariable
        @Parameter(description = "Occurrence id")
        occurrenceId: UUID,
        @RequestBody @Valid request: CreateCommentRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CommentResponse> {
        val comment = eventCommentService.create(eventId, occurrenceId, request, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    @Operation(
        summary = "List comments",
        description = "Paginated comments scoped by resourceId (occurrence id or post id). Omit targetId to list top-level comments for that resource, or pass a comment id for replies.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Comments returned"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "Event or occurrence not found"),
        ],
    )
    @GetMapping
    fun listComments(
        @PathVariable
        @Parameter(description = "Event id")
        eventId: UUID,
        @PathVariable
        @Parameter(description = "Occurrence id")
        occurrenceId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Target id to list under (defaults to occurrence id)")
        targetId: UUID?,
        @PageableDefault(size = 20)
        pageable: Pageable,
    ): ResponseEntity<Page<CommentResponse>> {
        return ResponseEntity.ok(
            eventCommentService.list(eventId, occurrenceId, targetId ?: occurrenceId, pageable),
        )
    }

    @Operation(summary = "Like comment")
    @PostMapping("/{commentId}/likes")
    fun likeComment(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PathVariable commentId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        eventCommentService.like(eventId, occurrenceId, commentId, jwt)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Unlike comment")
    @DeleteMapping("/{commentId}/likes")
    fun unlikeComment(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PathVariable commentId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        eventCommentService.unlike(eventId, occurrenceId, commentId, jwt)
        return ResponseEntity.noContent().build()
    }
}
