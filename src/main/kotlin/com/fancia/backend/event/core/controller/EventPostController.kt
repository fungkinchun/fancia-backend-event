package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.EventPostService
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
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
@RequestMapping("/api/events/{eventId}/occurrences/{occurrenceId}/posts")
@Tag(name = "Event Occurrence Posts", description = "Posts on event occurrences")
@SecurityRequirement(name = "bearerAuth")
class EventPostController(
    private val eventPostService: EventPostService,
) {
    @Operation(
        summary = "Create post on occurrence",
        description = "Creates a post with optional body and media (images, videos, etc.) from presigned upload. Caller must be a participant.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Post created"),
            ApiResponse(responseCode = "400", description = "Validation error"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "403", description = "Not allowed to post on this occurrence"),
            ApiResponse(responseCode = "404", description = "Event or occurrence not found"),
        ],
    )
    @PostMapping
    fun createPost(
        @PathVariable @Parameter(description = "Event id") eventId: UUID,
        @PathVariable @Parameter(description = "Occurrence id") occurrenceId: UUID,
        @RequestBody @Valid request: CreatePostBody,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        val post = eventPostService.create(eventId, occurrenceId, request, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(post)
    }

    @Operation(summary = "List posts on occurrence", description = "Paginated posts for the occurrence, newest first.")
    @GetMapping
    fun listPosts(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<PostResponse>> {
        return ResponseEntity.ok(eventPostService.list(eventId, occurrenceId, pageable))
    }

    @Operation(summary = "Get post on occurrence")
    @GetMapping("/{postId}")
    fun getPost(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PathVariable postId: UUID,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(eventPostService.get(eventId, occurrenceId, postId))
    }

    @Operation(summary = "Update post")
    @PutMapping("/{postId}")
    fun updatePost(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PathVariable postId: UUID,
        @RequestBody @Valid request: UpdatePostRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(eventPostService.update(eventId, occurrenceId, postId, request, jwt))
    }

    @Operation(summary = "Like post")
    @PostMapping("/{postId}/likes")
    fun likePost(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        eventPostService.like(eventId, occurrenceId, postId, jwt)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{postId}/likes")
    fun unlikePost(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        eventPostService.unlike(eventId, occurrenceId, postId, jwt)
        return ResponseEntity.noContent().build()
    }
}
