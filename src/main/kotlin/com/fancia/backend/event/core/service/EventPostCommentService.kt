package com.fancia.backend.event.core.service

import com.fancia.backend.event.external.CommonInternalClient
import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*

@Service
class EventPostCommentService(
    private val eventPostService: EventPostService,
    private val commonInternalClient: CommonInternalClient,
) {
    fun create(
        eventId: UUID,
        postId: UUID,
        request: CreateCommentRequest,
        jwt: Jwt,
    ): CommentResponse {
        val requesterId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        eventPostService.get(eventId, postId, jwt)
        return commonInternalClient.createComment(
            CreateCommentRequest(
                targetId = eventId,
                authorUserId = requesterId,
                body = request.body,
                parentId = request.parentId,
                postId = postId,
            )
        )
    }

    fun list(eventId: UUID, postId: UUID, pageable: Pageable, jwt: Jwt): Page<CommentResponse> {
        eventPostService.get(eventId, postId, jwt)
        return commonInternalClient.listComments(targetId = null, postId = postId, pageable = pageable)
    }

    fun get(eventId: UUID, postId: UUID, commentId: UUID, jwt: Jwt): CommentResponse {
        eventPostService.get(eventId, postId, jwt)
        val comment = commonInternalClient.getComment(commentId)
        if (comment.targetId != eventId || comment.postId != postId) {
            throw EventNotFoundException(eventId)
        }
        return comment
    }

    fun like(eventId: UUID, postId: UUID, commentId: UUID, jwt: Jwt) {
        get(eventId, postId, commentId, jwt)
        commonInternalClient.likeComment(commentId)
    }

    fun unlike(eventId: UUID, postId: UUID, commentId: UUID, jwt: Jwt) {
        get(eventId, postId, commentId, jwt)
        commonInternalClient.unlikeComment(commentId)
    }
}
