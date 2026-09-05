package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.BlockedResourceRepository
import com.fancia.backend.event.external.UserInternalClient
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.moderation.core.dto.BlockedResourceResponse
import com.fancia.backend.shared.common.moderation.core.dto.CreateBlockedResourceRequest
import com.fancia.backend.shared.common.moderation.core.entity.BlockedResource
import com.fancia.backend.shared.common.moderation.core.entity.BlockedResourceId
import com.fancia.backend.shared.common.moderation.core.enums.BlockedResourceType
import com.fancia.backend.shared.common.moderation.core.exception.UnsupportedBlockedResourceTypeException
import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BlockedResourceService(
    private val blockedResourceRepository: BlockedResourceRepository,
    private val userInternalClient: UserInternalClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun block(request: CreateBlockedResourceRequest, jwt: Jwt): BlockedResourceResponse {
        val userId = currentUserId(jwt)
        validateOwnedType(request.resourceType)
        val id = BlockedResourceId(
            userId = userId,
            resourceType = request.resourceType,
            resourceId = request.resourceId,
        )
        val saved = blockedResourceRepository.findById(id).orElse(null)
            ?: blockedResourceRepository.save(BlockedResource(id))
        return saved.toResponse()
    }

    @Transactional
    fun unblock(resourceType: BlockedResourceType, resourceId: UUID, jwt: Jwt) {
        val userId = currentUserId(jwt)
        validateOwnedType(resourceType)
        blockedResourceRepository.deleteByIdUserIdAndIdResourceTypeAndIdResourceId(
            userId,
            resourceType,
            resourceId,
        )
    }

    @Transactional(readOnly = true)
    fun list(
        resourceType: BlockedResourceType?,
        jwt: Jwt,
        pageable: Pageable,
    ): Page<BlockedResourceResponse> {
        val userId = currentUserId(jwt)
        val page = if (resourceType == null) {
            blockedResourceRepository.findByIdUserId(userId, pageable)
        } else {
            validateOwnedType(resourceType)
            blockedResourceRepository.findByIdUserIdAndIdResourceType(userId, resourceType, pageable)
        }
        return page.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun blockedEventIds(userId: UUID): Set<UUID> =
        blockedResourceRepository.findAllByIdUserIdAndIdResourceTypeIn(
            userId,
            listOf(BlockedResourceType.EVENT),
        ).map { it.id.resourceId }.toSet()

    fun loadUserAndTagBlocks(userId: UUID): Pair<Set<UUID>, Set<UUID>> {
        return try {
            val response = userInternalClient.getBlocked(
                userId,
                listOf(BlockedResourceType.USER, BlockedResourceType.TAG),
            )
            val blocked = response.blocked
            Pair(
                blocked[BlockedResourceType.USER].orEmpty().toSet(),
                blocked[BlockedResourceType.TAG].orEmpty().toSet(),
            )
        } catch (ex: FeignException) {
            log.warn("Failed to load blocked USER/TAG for userId={}", userId, ex)
            Pair(emptySet(), emptySet())
        }
    }

    fun loadPostVisibilityBlocks(userId: UUID): Pair<Set<UUID>, Set<UUID>> {
        return try {
            val response = userInternalClient.getBlocked(
                userId,
                listOf(BlockedResourceType.POST, BlockedResourceType.USER),
            )
            val blocked = response.blocked
            Pair(
                blocked[BlockedResourceType.POST].orEmpty().toSet(),
                blocked[BlockedResourceType.USER].orEmpty().toSet(),
            )
        } catch (ex: FeignException) {
            log.warn("Failed to load blocked POST/USER for userId={}", userId, ex)
            Pair(emptySet(), emptySet())
        }
    }

    fun loadCommentVisibilityBlocks(userId: UUID): Pair<Set<UUID>, Set<UUID>> {
        return try {
            val response = userInternalClient.getBlocked(
                userId,
                listOf(BlockedResourceType.COMMENT, BlockedResourceType.USER),
            )
            val blocked = response.blocked
            Pair(
                blocked[BlockedResourceType.COMMENT].orEmpty().toSet(),
                blocked[BlockedResourceType.USER].orEmpty().toSet(),
            )
        } catch (ex: FeignException) {
            log.warn("Failed to load blocked COMMENT/USER for userId={}", userId, ex)
            Pair(emptySet(), emptySet())
        }
    }

    fun validateOwnedType(resourceType: BlockedResourceType) {
        if (resourceType != BlockedResourceType.EVENT) {
            throw UnsupportedBlockedResourceTypeException()
        }
    }

    private fun currentUserId(jwt: Jwt): UUID =
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()

    private fun BlockedResource.toResponse() = BlockedResourceResponse(
        resourceType = id.resourceType,
        resourceId = id.resourceId,
        createdAt = createdAt,
    )
}
