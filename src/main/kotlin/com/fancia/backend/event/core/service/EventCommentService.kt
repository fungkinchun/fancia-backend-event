package com.fancia.backend.event.core.service

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
    private val eventOccurrenceService: EventOccurrenceService,
    private val commonInternalClient: CommonInternalClient,
) {
    fun create(
        eventId: UUID,
        occurrenceId: UUID,
        request: CreateCommentRequest,
        jwt: Jwt,
    ): CommentResponse {
        validateOccurrence(eventId, occurrenceId)
        return commonInternalClient.createComment(request)
    }

    fun list(
        eventId: UUID,
        occurrenceId: UUID,
        targetId: UUID,
        pageable: Pageable,
    ): Page<CommentResponse> {
        validateOccurrence(eventId, occurrenceId)
        return commonInternalClient.listComments(targetId, occurrenceId, pageable)
    }

    fun get(eventId: UUID, occurrenceId: UUID, commentId: UUID): CommentResponse {
        validateOccurrence(eventId, occurrenceId)
        val comment = commonInternalClient.getComment(commentId)
        if (comment.resourceId != occurrenceId) {
            throw CommentNotFoundException(commentId)
        }
        return comment
    }

    fun like(eventId: UUID, occurrenceId: UUID, commentId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(eventId, occurrenceId, commentId)
        commonInternalClient.likeComment(commentId)
    }

    fun unlike(eventId: UUID, occurrenceId: UUID, commentId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(eventId, occurrenceId, commentId)
        commonInternalClient.unlikeComment(commentId)
    }

    private fun validateOccurrence(eventId: UUID, occurrenceId: UUID) {
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
        eventOccurrenceService.getOccurrence(eventId, occurrenceId)
    }
}
