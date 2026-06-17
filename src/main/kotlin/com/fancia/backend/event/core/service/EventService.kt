package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.event.core.entity.EventParticipantId
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.external.CommonServiceClient
import com.fancia.backend.event.mapper.EventMapper
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.common.tag.core.dto.TagItemRequest
import com.fancia.backend.shared.event.core.dto.CreateEventRequest
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventRequest
import com.fancia.backend.shared.event.core.enums.EventVisibility
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import com.fancia.backend.shared.event.core.exception.GroupEventRequiresInterestGroupsException
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val eventMapper: EventMapper,
    private val commonServiceClient: CommonServiceClient,
    private val eventLocationResolver: EventLocationResolver,
) {
    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): Event? {
        return eventRepository.findByIdAndCreatedBy(id, createdBy)
    }

    fun findById(id: UUID): EventResponse {
        return eventRepository.findById(id)
            .map(eventMapper::toDto)
            .orElseThrow { EventNotFoundException(id) }
    }

    @Transactional
    fun create(request: @Valid CreateEventRequest, jwt: Jwt): EventResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val visibility = request.visibility ?: EventVisibility.PUBLIC
        validateVisibility(visibility, request.interestGroups)
        eventMapper.toBean(request).let {
            it.createdBy = currentUserId
            it.visibility = visibility
            applyTags(it.tags, request.tags)
            it.links.clear()
            it.links.addAll(request.links.map { link -> Link(type = link.type, url = link.url) })
            eventLocationResolver.apply(it, request.location)
            val event = eventRepository.save(it).let(eventMapper::toDto)
            event.id?.let { eventId ->
                val createdBy = EventParticipant(
                    EventParticipantId(
                        eventId = eventId,
                        userId = currentUserId
                    )
                )
                createdBy.event = it
                it.participants.add(createdBy)
            }
            return event
        }
    }

    @Transactional
    fun update(id: UUID, request: @Valid UpdateEventRequest, jwt: Jwt): EventResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val event = findByIdAndCreatedBy(id, currentUserId) ?: throw EventNotFoundException(id)
        val visibility = request.visibility ?: event.visibility
        validateVisibility(visibility, event.interestGroups)
        return eventRepository.save(
            eventMapper.toBean(request, event).apply {
                this.visibility = visibility
                applyTags(this.tags, request.tags)
                this.links.clear()
                this.links.addAll(request.links.map { link -> Link(type = link.type, url = link.url) })
                eventLocationResolver.apply(this, request.location)
            }
        ).let(eventMapper::toDto)
    }

    @Transactional
    fun removeTagFromAllEvents(tagId: UUID) {
        val eventsWithTag = eventRepository.findByTagId(tagId)
        for (event in eventsWithTag) {
            event.tags.remove(tagId)
        }
        if (eventsWithTag.isNotEmpty()) {
            eventRepository.saveAll(eventsWithTag)
        }
    }

    fun findAll(
        name: String?,
        description: String?,
        tags: String?,
        interestGroupId: UUID?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double?,
        pageable: Pageable
    ): Page<EventResponse> {
        if (latitude != null && longitude != null && radiusKm != null) {
            val radiusMeters = radiusKm * 1000
            return eventRepository.findNearby(
                latitude,
                longitude,
                radiusMeters,
                interestGroupId,
                pageable,
            ).map(eventMapper::toDto)
        }

        return eventRepository.findAll(
            name?.trim() ?: "",
            description?.trim() ?: "",
            tags?.trim() ?: "",
            interestGroupId,
            pageable
        ).map(eventMapper::toDto)
    }

    private fun applyTags(tags: MutableSet<UUID>, requestTags: Set<TagItemRequest>) {
        val resolved = requestTags
            .groupBy { it.type }
            .flatMap { (type, items) ->
                val names = items.map { it.name }.toSet()
                if (names.isEmpty()) emptyList() else commonServiceClient.getTags(names, type).content
            }
            .mapNotNull { it.id }
        tags.clear()
        tags.addAll(resolved)
    }

    private fun validateVisibility(visibility: EventVisibility, interestGroups: Set<UUID>) {
        if (visibility == EventVisibility.GROUP && interestGroups.isEmpty()) {
            throw GroupEventRequiresInterestGroupsException()
        }
    }
}
