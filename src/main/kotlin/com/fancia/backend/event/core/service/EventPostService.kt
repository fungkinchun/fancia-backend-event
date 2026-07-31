package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.external.CommonInternalClient
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.CreatePostRequest
import com.fancia.backend.shared.common.post.core.dto.PostMediaItem
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import com.fancia.backend.shared.upload.storage.core.enums.UploadScope
import com.fancia.backend.shared.upload.storage.core.service.FileStorageService
import com.fancia.backend.shared.upload.storage.core.service.moveTmpToDedicatedPath
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*

@Service
class EventPostService(
    private val eventRepository: EventRepository,
    private val eventOccurrenceService: EventOccurrenceService,
    private val commonInternalClient: CommonInternalClient,
    private val fileUploadService: FileStorageService,
) {
    fun create(
        eventId: UUID,
        occurrenceId: UUID,
        request: CreatePostBody,
        jwt: Jwt,
    ): PostResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        validateOccurrence(eventId, occurrenceId)
        return commonInternalClient.createPost(
            CreatePostRequest(
                targetId = occurrenceId,
                authorUserId = currentUserId,
                body = request.body,
                media = dedicateMedia(request.media, occurrenceId),
                featured = request.featured,
                pinned = request.pinned,
            ),
        )
    }

    fun update(
        eventId: UUID,
        occurrenceId: UUID,
        postId: UUID,
        request: UpdatePostRequest,
        jwt: Jwt,
    ): PostResponse {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        validateOccurrence(eventId, occurrenceId)
        val post = commonInternalClient.updatePost(
            postId,
            request.copy(media = dedicateMedia(request.media, occurrenceId)),
        )
        if (post.targetId != occurrenceId) {
            throw EventNotFoundException(eventId)
        }
        return post
    }

    fun like(eventId: UUID, occurrenceId: UUID, postId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(eventId, occurrenceId, postId)
        commonInternalClient.likePost(postId)
    }

    fun unlike(eventId: UUID, occurrenceId: UUID, postId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(eventId, occurrenceId, postId)
        commonInternalClient.unlikePost(postId)
    }

    fun list(eventId: UUID, occurrenceId: UUID, pageable: Pageable): Page<PostResponse> {
        validateOccurrence(eventId, occurrenceId)
        return commonInternalClient.listPosts(occurrenceId, pageable)
    }

    fun get(eventId: UUID, occurrenceId: UUID, postId: UUID): PostResponse {
        validateOccurrence(eventId, occurrenceId)
        val post = commonInternalClient.getPost(postId)
        if (post.targetId != occurrenceId) {
            throw EventNotFoundException(eventId)
        }
        return post
    }

    private fun validateOccurrence(eventId: UUID, occurrenceId: UUID) {
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
        eventOccurrenceService.getOccurrence(eventId, occurrenceId)
    }

    private fun dedicateMedia(media: List<PostMediaItem>, occurrenceId: UUID): List<PostMediaItem> =
        media.map { item ->
            item.copy(
                objectKey = fileUploadService.moveTmpToDedicatedPath(
                    item.objectKey,
                    UploadScope.EVENT,
                    occurrenceId,
                ),
            )
        }
}
