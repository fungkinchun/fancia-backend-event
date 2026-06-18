package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.event.core.entity.EventParticipantId
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.external.CommonServiceClient
import com.fancia.backend.event.mapper.toDto
import com.fancia.backend.event.mapper.toEntity
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.tag.core.dto.CreateTagsRequest
import com.fancia.backend.shared.common.tag.core.dto.TagItemRequest
import com.fancia.backend.shared.event.core.dto.CreateEventRequest
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventRequest
import com.fancia.backend.shared.event.core.enums.EventVisibility
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import com.fancia.backend.shared.event.core.exception.GroupEventRequiresInterestGroupsException
import com.fancia.backend.shared.event.core.exception.InvalidEventScheduleException
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val commonServiceClient: CommonServiceClient,
    private val eventLocationResolver: EventLocationResolver,
) {
    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): Event? {
        return eventRepository.findByIdAndCreatedBy(id, createdBy)
    }

    fun findById(id: UUID): EventResponse {
        return eventRepository.findById(id)
            .map { it.toDto() }
            .orElseThrow { EventNotFoundException(id) }
    }

    @Transactional
    fun create(request: @Valid CreateEventRequest, jwt: Jwt): EventResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val visibility = request.visibility ?: EventVisibility.PUBLIC
        validateVisibility(visibility, request.interestGroups)
        validateSchedule(request.startTime, request.endTime)
        request.toEntity().let {
            it.createdBy = currentUserId
            it.visibility = visibility
            applyTags(it.tags, request.tags)
            eventLocationResolver.apply(it, request.location)
            val event = eventRepository.save(it).toDto()
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
        validateSchedule(request.startTime, request.endTime)
        return eventRepository.save(
            request.toEntity(event).apply {
                this.visibility = visibility
                applyTags(this.tags, request.tags)
                eventLocationResolver.apply(this, request.location)
            }
        ).toDto()
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
        tagIds: List<UUID>?,
        interestGroupId: UUID?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double?,
        pageable: Pageable
    ): Page<EventResponse> {
        if (latitude != null && longitude != null && radiusKm != null) {
            val radiusMeters = radiusKm * 1000
            return filterDiscoverable(
                eventRepository.findNearby(latitude, longitude, radiusMeters, pageable),
                interestGroupId,
            ).map { it.toDto() }
        }

        val trimmedName = name?.trim().orEmpty()
        val trimmedDescription = description?.trim().orEmpty()
        val hasText = trimmedName.isNotEmpty() || trimmedDescription.isNotEmpty()
        val hasTagIds = !tagIds.isNullOrEmpty()

        val events = when {
            !hasText && !hasTagIds ->
                eventRepository.findAll(pageable)

            !hasText && hasTagIds ->
                eventRepository.findByTagIdIn(tagIds!!, pageable)

            else ->
                eventRepository.search(
                    trimmedName,
                    trimmedDescription,
                    hasTagIds,
                    tagIds.orEmpty(),
                    pageable,
                )
        }
        return filterDiscoverable(events, interestGroupId).map { it.toDto() }
    }

    private fun applyTags(tags: MutableSet<UUID>, requestTags: Set<TagItemRequest>) {
        tags.clear()
        if (requestTags.isEmpty()) return
        val resolved = commonServiceClient.createTags(
            CreateTagsRequest(tags = requestTags.toList()),
            size = requestTags.size,
        ).content.mapNotNull { it.id }
        tags.addAll(resolved)
    }

    private fun validateVisibility(visibility: EventVisibility, interestGroups: Set<UUID>) {
        if (visibility == EventVisibility.GROUP && interestGroups.isEmpty()) {
            throw GroupEventRequiresInterestGroupsException()
        }
    }

    private fun validateSchedule(startTime: java.time.LocalDateTime, endTime: java.time.LocalDateTime) {
        if (!endTime.isAfter(startTime)) {
            throw InvalidEventScheduleException()
        }
    }

    private fun isDiscoverable(event: Event, interestGroupId: UUID?): Boolean {
        return when (event.visibility) {
            EventVisibility.PRIVATE -> false
            EventVisibility.GROUP ->
                interestGroupId != null && event.interestGroups.contains(interestGroupId)
            EventVisibility.PUBLIC ->
                interestGroupId == null || event.interestGroups.contains(interestGroupId)
        }
    }

    private fun filterDiscoverable(page: Page<Event>, interestGroupId: UUID?): Page<Event> {
        val filtered = page.content.filter { isDiscoverable(it, interestGroupId) }
        return PageImpl(filtered, page.pageable, filtered.size.toLong())
    }
}
