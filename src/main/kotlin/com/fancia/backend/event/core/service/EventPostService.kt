package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.external.CommonInternalClient
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.moderation.core.support.PostVisibility
import com.fancia.backend.shared.common.post.core.dto.CastPollVoteRequest
import com.fancia.backend.shared.common.post.core.enums.PostKind
import com.fancia.backend.shared.common.post.core.enums.PostStatus
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.CreatePostRequest
import com.fancia.backend.shared.common.post.core.dto.PostMediaItem
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.shared.common.post.core.exception.PostNotFoundException
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import com.fancia.backend.shared.upload.storage.core.enums.UploadScope
import com.fancia.backend.shared.upload.storage.core.service.FileStorageService
import com.fancia.backend.shared.upload.storage.core.service.moveTmpToDedicatedPath
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*

@Service
class EventPostService(
    private val eventRepository: EventRepository,
    private val commonInternalClient: CommonInternalClient,
    private val fileUploadService: FileStorageService,
    private val blockedResourceService: BlockedResourceService,
) {
    fun create(eventId: UUID, request: CreatePostBody, jwt: Jwt): PostResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        requireEvent(eventId)
        return commonInternalClient.createPost(
            CreatePostRequest(
                targetId = eventId,
                authorUserId = currentUserId,
                body = request.body,
                media = dedicateMedia(request.mediaOrEmpty(), eventId),
                status = request.statusOrDefault(),
                expiredAt = request.expiredAt,
                kind = request.kindOrDefault(),
                poll = request.poll,
            ),
        )
    }

    fun update(eventId: UUID, postId: UUID, request: UpdatePostRequest, jwt: Jwt): PostResponse {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        requireEvent(eventId)
        val post = commonInternalClient.updatePost(
            postId,
            request.copy(media = dedicateMedia(request.media, eventId)),
        )
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

    fun vote(eventId: UUID, postId: UUID, request: CastPollVoteRequest, jwt: Jwt): PostResponse {
        get(eventId, postId, jwt)
        val post = commonInternalClient.voteOnPost(postId, request)
        if (post.targetId != eventId) {
            throw EventNotFoundException(eventId)
        }
        return post
    }

    fun list(
        eventId: UUID,
        kind: PostKind? = null,
        status: List<PostStatus>? = null,
        pageable: Pageable,
        jwt: Jwt? = null,
    ): Page<PostResponse> {
        requireEvent(eventId)
        val page = commonInternalClient.listPosts(eventId, kind, status, pageable)
        return filterBlocked(page, pageable, jwt)
    }

    fun get(eventId: UUID, postId: UUID, jwt: Jwt? = null): PostResponse {
        requireEvent(eventId)
        val post = commonInternalClient.getPost(postId)
        if (post.targetId != eventId) {
            throw EventNotFoundException(eventId)
        }
        assertVisible(post, jwt)
        return post
    }

    private fun filterBlocked(
        page: Page<PostResponse>,
        pageable: Pageable,
        jwt: Jwt?,
    ): Page<PostResponse> {
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) } ?: return page
        if (page.isEmpty) return page
        val (blockedPosts, blockedUsers) = blockedResourceService.loadPostVisibilityBlocks(viewerId)
        if (blockedPosts.isEmpty() && blockedUsers.isEmpty()) return page
        val kept = page.content.filter {
            PostVisibility.isVisibleToViewer(it, blockedPosts, blockedUsers)
        }
        if (kept.size == page.content.size) return page
        return PageImpl(kept, pageable, page.totalElements)
    }

    private fun assertVisible(post: PostResponse, jwt: Jwt?) {
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) } ?: return
        val (blockedPosts, blockedUsers) = blockedResourceService.loadPostVisibilityBlocks(viewerId)
        if (!PostVisibility.isVisibleToViewer(post, blockedPosts, blockedUsers)) {
            throw PostNotFoundException(post.id)
        }
    }

    private fun requireEvent(eventId: UUID) {
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
    }

    private fun dedicateMedia(media: List<PostMediaItem>, eventId: UUID): List<PostMediaItem> =
        media.map { item ->
            item.copy(
                objectKey = fileUploadService.moveTmpToDedicatedPath(
                    item.objectKey,
                    UploadScope.EVENT,
                    eventId,
                ),
            )
        }
}
