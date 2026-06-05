package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.EventPostCommentService
import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import io.swagger.v3.oas.annotations.Operation
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
@RequestMapping("/api/events/{eventId}/posts/{postId}/comments")
@Tag(name = "Event Post Comments", description = "Comments on event posts")
@SecurityRequirement(name = "bearerAuth")
class EventPostCommentController(
    private val eventPostCommentService: EventPostCommentService,
) {
    @Operation(summary = "Create comment on event post")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Comment created"),
            ApiResponse(responseCode = "400", description = "Validation error"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "Event or post not found"),
        ]
    )
    @PostMapping
    fun createComment(
        @PathVariable eventId: UUID,
        @PathVariable postId: UUID,
        @RequestBody @Valid request: CreateCommentRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CommentResponse> {
        val comment = eventPostCommentService.create(eventId, postId, request, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    @Operation(summary = "List comments on event post")
    @GetMapping
    fun listComments(
        @PathVariable eventId: UUID,
        @PathVariable postId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Page<CommentResponse>> {
        return ResponseEntity.ok(eventPostCommentService.list(eventId, postId, pageable, jwt))
    }

    @Operation(summary = "Like comment on event post")
    @PostMapping("/{commentId}/likes")
    fun likeComment(
        @PathVariable eventId: UUID,
        @PathVariable postId: UUID,
        @PathVariable commentId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        eventPostCommentService.like(eventId, postId, commentId, jwt)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Unlike comment on event post")
    @DeleteMapping("/{commentId}/likes")
    fun unlikeComment(
        @PathVariable eventId: UUID,
        @PathVariable postId: UUID,
        @PathVariable commentId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        eventPostCommentService.unlike(eventId, postId, commentId, jwt)
        return ResponseEntity.noContent().build()
    }
}
