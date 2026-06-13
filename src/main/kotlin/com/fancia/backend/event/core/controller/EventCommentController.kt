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
        summary = "List comments",
        description = "Paginated comments scoped by resourceId (event id or post id). Omit targetId to list top-level comments for that resource, or pass a comment id for replies.",
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
        @RequestParam(required = false)
        @Parameter(description = "Target id to list under (defaults to resourceId)")
        targetId: UUID?,
        @PageableDefault(size = 20)
        pageable: Pageable,
    ): ResponseEntity<Page<CommentResponse>> {
        return ResponseEntity.ok(eventCommentService.list(eventId, targetId ?: eventId, pageable))
    }

    @Operation(summary = "Like comment")
    @PostMapping("/{commentId}/likes")
    fun likeComment(
        @PathVariable eventId: UUID,
        @PathVariable commentId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        eventCommentService.like(eventId, commentId, jwt)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Unlike comment")
    @DeleteMapping("/{commentId}/likes")
    fun unlikeComment(
        @PathVariable eventId: UUID,
        @PathVariable commentId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        eventCommentService.unlike(eventId, commentId, jwt)
        return ResponseEntity.noContent().build()
    }
}
