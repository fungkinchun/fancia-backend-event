package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.EventPostService
import com.fancia.backend.shared.common.post.core.dto.CastPollVoteRequest
import com.fancia.backend.shared.common.post.core.enums.PostKind
import com.fancia.backend.shared.common.post.core.enums.PostStatus
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
@RequestMapping("/api/events/{eventId}/posts")
@Tag(name = "Event Posts", description = "Posts on events (FEATURED status is the cover image)")
@SecurityRequirement(name = "bearerAuth")
class EventPostController(
    private val eventPostService: EventPostService,
) {
    @Operation(summary = "Create post on event", description = "Featured posts with media are used as the event cover.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Post created"),
            ApiResponse(responseCode = "400", description = "Validation error"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "Event not found"),
        ],
    )
    @PostMapping
    fun createPost(
        @PathVariable @Parameter(description = "Event id") eventId: UUID,
        @RequestBody @Valid request: CreatePostBody,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        val post = eventPostService.create(eventId, request, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(post)
    }

    @Operation(summary = "List posts on event", description = "Paginated posts for the event, newest first.")
    @GetMapping
    fun listPosts(
        @PathVariable eventId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Filter by post kind (TEXT or POLL)")
        kind: PostKind?,
        @RequestParam(required = false)
        @Parameter(description = "Filter by post status (repeatable)")
        status: List<PostStatus>?,
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<Page<PostResponse>> {
        return ResponseEntity.ok(eventPostService.list(eventId, kind, status, pageable, jwt))
    }

    @Operation(summary = "Get post on event")
    @GetMapping("/{postId}")
    fun getPost(
        @PathVariable eventId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(eventPostService.get(eventId, postId, jwt))
    }

    @Operation(summary = "Update post")
    @PutMapping("/{postId}")
    fun updatePost(
        @PathVariable eventId: UUID,
        @PathVariable postId: UUID,
        @RequestBody @Valid request: UpdatePostRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(eventPostService.update(eventId, postId, request, jwt))
    }

    @Operation(summary = "Like post")
    @PostMapping("/{postId}/likes")
    fun likePost(
        @PathVariable eventId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        eventPostService.like(eventId, postId, jwt)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{postId}/likes")
    fun unlikePost(
        @PathVariable eventId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        eventPostService.unlike(eventId, postId, jwt)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Vote on poll post")
    @PostMapping("/{postId}/votes")
    fun voteOnPost(
        @PathVariable eventId: UUID,
        @PathVariable postId: UUID,
        @RequestBody @Valid request: CastPollVoteRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(eventPostService.vote(eventId, postId, request, jwt))
    }
}
