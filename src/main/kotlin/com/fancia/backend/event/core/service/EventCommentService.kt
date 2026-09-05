package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.external.CommonInternalClient
import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import com.fancia.backend.shared.common.comment.core.exception.CommentNotFoundException
import com.fancia.backend.shared.common.moderation.core.support.CommentVisibility
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
    private val blockedResourceService: BlockedResourceService,
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
        jwt: Jwt? = null,
    ): Page<CommentResponse> {
        validateOccurrence(eventId, occurrenceId)
        val page = commonInternalClient.listComments(targetId, occurrenceId, pageable)
        return filterBlocked(page, pageable, jwt)
    }

    fun get(
        eventId: UUID,
        occurrenceId: UUID,
        commentId: UUID,
        jwt: Jwt? = null,
    ): CommentResponse {
        validateOccurrence(eventId, occurrenceId)
        val comment = commonInternalClient.getComment(commentId)
        if (comment.resourceId != occurrenceId) {
            throw CommentNotFoundException(commentId)
        }
        assertVisible(comment, jwt)
        return comment
    }

    fun like(eventId: UUID, occurrenceId: UUID, commentId: UUID, jwt: Jwt) {
        get(eventId, occurrenceId, commentId, jwt)
        commonInternalClient.likeComment(commentId)
    }

    fun unlike(eventId: UUID, occurrenceId: UUID, commentId: UUID, jwt: Jwt) {
        get(eventId, occurrenceId, commentId, jwt)
        commonInternalClient.unlikeComment(commentId)
    }

    private fun filterBlocked(
        page: Page<CommentResponse>,
        pageable: Pageable,
        jwt: Jwt?,
    ): Page<CommentResponse> {
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) } ?: return page
        val (blockedComments, blockedUsers) = blockedResourceService.loadCommentVisibilityBlocks(viewerId)
        return CommentVisibility.filterPage(page, pageable, blockedComments, blockedUsers)
    }

    private fun assertVisible(comment: CommentResponse, jwt: Jwt?) {
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) } ?: return
        val (blockedComments, blockedUsers) = blockedResourceService.loadCommentVisibilityBlocks(viewerId)
        if (!CommentVisibility.isVisibleToViewer(comment, blockedComments, blockedUsers)) {
            throw CommentNotFoundException(comment.id)
        }
    }

    private fun validateOccurrence(eventId: UUID, occurrenceId: UUID) {
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
        eventOccurrenceService.getOccurrence(eventId, occurrenceId)
    }
}
