package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.external.CommonServiceClient
import com.fancia.backend.event.external.UserServiceClient
import com.fancia.backend.shared.common.tag.core.dto.TagItemRequest
import com.fancia.backend.shared.common.tag.core.enums.TagType
import com.fancia.backend.shared.user.core.dto.UpdateUserRequest
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import java.util.*

@Service
class EventUserTagSyncService(
    private val commonServiceClient: CommonServiceClient,
    private val userServiceClient: UserServiceClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun syncEventTagsOnJoin(userId: UUID, event: Event) {
        if (event.tags.isEmpty()) return
        val currentUserId = currentUserIdOrNull() ?: return
        if (currentUserId != userId) return

        try {
            val eventTags = commonServiceClient.getTagsByIds(event.tags)
            if (eventTags.isEmpty()) return

            val currentUser = userServiceClient.getCurrentUser()
            val existingTagRequests = if (currentUser.tags.isEmpty()) {
                emptySet()
            } else {
                commonServiceClient.getTagsByIds(currentUser.tags)
                    .map { TagItemRequest(name = it.name, type = it.type) }
                    .toSet()
            }
            val eventTagRequests = eventTags
                .map { TagItemRequest(name = it.name, type = TagType.EVENT) }
                .toSet()
            val mergedTags = (existingTagRequests + eventTagRequests)
                .distinctBy { it.name to it.type }
                .toSet()

            userServiceClient.updateUser(UpdateUserRequest(tags = mergedTags))
        } catch (ex: Exception) {
            log.warn("Failed to sync event tags to user {} for event {}", userId, event.id, ex)
        }
    }

    private fun currentUserIdOrNull(): UUID? {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication !is JwtAuthenticationToken) return null
        return authentication.token.getClaimAsString("userId")?.let(UUID::fromString)
    }
}
