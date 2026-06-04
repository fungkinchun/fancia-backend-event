package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.EventPostService
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.PostResponse
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
@RequestMapping("/api/events/{eventId}/posts")
@Tag(name = "Event Posts", description = "Posts on events")
@SecurityRequirement(name = "bearerAuth")
class EventPostController(
    private val eventPostService: EventPostService,
) {
    @Operation(
        summary = "Create post on event",
        description = "Creates a post with optional body and media (images, videos, etc.) from presigned upload. Caller must be a participant.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Post created"),
            ApiResponse(responseCode = "400", description = "Validation error"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "403", description = "Not allowed to post on this event"),
            ApiResponse(responseCode = "404", description = "Event not found"),
        ]
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
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Page<PostResponse>> {
        return ResponseEntity.ok(eventPostService.list(eventId, pageable, jwt))
    }

    @Operation(summary = "Get post on event")
    @GetMapping("/{postId}")
    fun getPost(
        @PathVariable eventId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(eventPostService.get(eventId, postId, jwt))
    }
}
