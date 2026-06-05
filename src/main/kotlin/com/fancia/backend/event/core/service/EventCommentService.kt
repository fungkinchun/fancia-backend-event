package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.EventParticipantRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.external.CommonInternalClient
import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import com.fancia.backend.shared.common.comment.core.exception.CommentAccessDeniedException
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*

@Service
class EventCommentService(
    private val eventRepository: EventRepository,
    private val eventParticipantRepository: EventParticipantRepository,
    private val commonInternalClient: CommonInternalClient,
) {
    fun create(eventId: UUID, request: CreateCommentRequest, jwt: Jwt): CommentResponse {
        val requesterId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
        if (!eventParticipantRepository.existsByIdEventIdAndIdUserId(eventId, requesterId)) {
            throw CommentAccessDeniedException(eventId)
        }
        return commonInternalClient.createComment(
            CreateCommentRequest(
                targetId = eventId,
                authorUserId = requesterId,
                body = request.body,
                parentId = request.parentId,
            )
        )
    }

    fun list(eventId: UUID, pageable: Pageable, jwt: Jwt): Page<CommentResponse> {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        assertEventExists(eventId)
        return commonInternalClient.listComments(eventId, null, pageable)
    }

    fun get(eventId: UUID, commentId: UUID, jwt: Jwt): CommentResponse {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        assertEventExists(eventId)
        val comment = commonInternalClient.getComment(commentId)
        if (comment.targetId != eventId) {
            throw EventNotFoundException(eventId)
        }
        return comment
    }

    fun like(eventId: UUID, commentId: UUID, jwt: Jwt) {
        get(eventId, commentId, jwt)
        commonInternalClient.likeComment(commentId)
    }

    fun unlike(eventId: UUID, commentId: UUID, jwt: Jwt) {
        get(eventId, commentId, jwt)
        commonInternalClient.unlikeComment(commentId)
    }

    private fun assertEventExists(eventId: UUID) {
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
    }
}
