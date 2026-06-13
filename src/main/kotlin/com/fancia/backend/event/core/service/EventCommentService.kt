package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.EventParticipantRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.external.CommonInternalClient
import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import com.fancia.backend.shared.common.comment.core.exception.CommentNotFoundException
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
    private val eventPostService: EventPostService,
    private val commonInternalClient: CommonInternalClient,
) {
    fun create(eventId: UUID, request: CreateCommentRequest, jwt: Jwt): CommentResponse {
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
        return commonInternalClient.createComment(request)
    }

    fun list(
        eventId: UUID,
        targetId: UUID,
        pageable: Pageable,
    ): Page<CommentResponse> {
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
        return commonInternalClient.listComments(targetId, eventId, pageable)
    }

    fun get(eventId: UUID, commentId: UUID): CommentResponse {
        val comment = commonInternalClient.getComment(commentId)
        if (comment.resourceId != eventId) {
            throw CommentNotFoundException(commentId)
        }
        return comment
    }

    fun like(eventId: UUID, commentId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(eventId, commentId)
        commonInternalClient.likeComment(commentId)
    }

    fun unlike(eventId: UUID, commentId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(eventId, commentId)
        commonInternalClient.unlikeComment(commentId)
    }
}
