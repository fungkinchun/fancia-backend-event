package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.repository.SavedResourceRepository
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.saved.core.dto.SavedResourceResponse
import com.fancia.backend.shared.common.saved.core.entity.SavedResource
import com.fancia.backend.shared.common.saved.core.entity.SavedResourceId
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SavedResourceService(
    private val savedResourceRepository: SavedResourceRepository,
    private val eventRepository: EventRepository,
) {
    @Transactional
    fun save(eventId: UUID, jwt: Jwt): SavedResourceResponse {
        val userId = currentUserId(jwt)
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
        val id = SavedResourceId(userId = userId, resourceId = eventId)
        val saved = savedResourceRepository.findById(id).orElse(null)
            ?: savedResourceRepository.save(SavedResource(id))
        return SavedResourceResponse(resourceId = saved.id.resourceId, createdAt = saved.createdAt)
    }

    @Transactional
    fun unsave(eventId: UUID, jwt: Jwt) {
        val userId = currentUserId(jwt)
        savedResourceRepository.deleteByIdUserIdAndIdResourceId(userId, eventId)
    }

    @Transactional(readOnly = true)
    fun listSavedPage(jwt: Jwt, pageable: Pageable): Page<SavedResource> {
        val userId = currentUserId(jwt)
        return savedResourceRepository.findByIdUserIdOrderByCreatedAtDesc(userId, pageable)
    }

    @Transactional(readOnly = true)
    fun isSaved(userId: UUID, eventId: UUID): Boolean =
        savedResourceRepository.existsByIdUserIdAndIdResourceId(userId, eventId)

    private fun currentUserId(jwt: Jwt): UUID =
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
