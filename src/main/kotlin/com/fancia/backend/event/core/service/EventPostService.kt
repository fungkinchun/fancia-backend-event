package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.EventParticipantRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.external.CommonInternalClient
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.CreatePostRequest
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.shared.common.post.core.exception.PostAccessDeniedException
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*

@Service
class EventPostService(
    private val eventRepository: EventRepository,
    private val eventParticipantRepository: EventParticipantRepository,
    private val commonInternalClient: CommonInternalClient,
) {
    fun create(eventId: UUID, request: CreatePostBody, jwt: Jwt): PostResponse {
        val requesterId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        assertCanPost(eventId, requesterId)
        return commonInternalClient.createPost(
            CreatePostRequest(
                targetId = eventId,
                authorUserId = requesterId,
                body = request.body,
                media = request.media,
                isFeatured = request.isFeatured,
                isPinned = request.isPinned,
            )
        )
    }

    fun update(
        eventId: UUID,
        postId: UUID,
        request: UpdatePostRequest,
        jwt: Jwt,
    ): PostResponse {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
        val post = commonInternalClient.updatePost(postId, request)
        if (post.targetId != eventId) {
            throw EventNotFoundException(eventId)
        }
        return post
    }

    fun like(eventId: UUID, postId: UUID, jwt: Jwt) {
        get(eventId, postId, jwt)
        commonInternalClient.likePost(postId)
    }

    fun unlike(eventId: UUID, postId: UUID, jwt: Jwt) {
        get(eventId, postId, jwt)
        commonInternalClient.unlikePost(postId)
    }

    fun list(eventId: UUID, pageable: Pageable, jwt: Jwt): Page<PostResponse> {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        assertEventExists(eventId)
        return commonInternalClient.listPosts(eventId, pageable)
    }

    fun get(eventId: UUID, postId: UUID, jwt: Jwt): PostResponse {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        assertEventExists(eventId)
        val post = commonInternalClient.getPost(postId)
        if (post.targetId != eventId) {
            throw EventNotFoundException(eventId)
        }
        return post
    }

    private fun assertEventExists(eventId: UUID) {
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
    }

    private fun assertCanPost(eventId: UUID, requesterId: UUID) {
        assertEventExists(eventId)
        if (!eventParticipantRepository.existsByIdEventIdAndIdUserId(eventId, requesterId)) {
            throw PostAccessDeniedException(eventId)
        }
    }
}
