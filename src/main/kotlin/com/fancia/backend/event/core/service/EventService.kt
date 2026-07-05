package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.event.core.entity.EventParticipantId
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.support.RecurringEventVisibility
import com.fancia.backend.event.core.support.SmartMatchEventRanker
import com.fancia.backend.event.core.support.SmartMatchPreferences
import com.fancia.backend.event.external.CommonServiceClient
import com.fancia.backend.event.mapper.toDto
import com.fancia.backend.event.mapper.toEntity
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.tag.core.dto.CreateTagsRequest
import com.fancia.backend.shared.common.tag.core.dto.TagItemRequest
import com.fancia.backend.shared.event.core.dto.CreateEventRequest
import com.fancia.backend.shared.event.core.dto.EventRecurrenceDto
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventRequest
import com.fancia.backend.shared.event.core.enums.EventVisibility
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import com.fancia.backend.shared.event.core.enums.ReservationStatus
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import com.fancia.backend.shared.event.core.exception.GroupEventRequiresInterestGroupsException
import com.fancia.backend.shared.event.core.exception.InvalidEventScheduleException
import com.fancia.backend.shared.event.core.model.RecurrenceDaysMask
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val commonServiceClient: CommonServiceClient,
    private val eventLocationResolver: EventLocationResolver,
    private val smartMatchEventRanker: SmartMatchEventRanker,
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
        request.recurrence?.let { RecurringEventVisibility.validateRecurrence(it) }
        request.toEntity().let {
            it.createdBy = currentUserId
            it.visibility = visibility
            applyTags(it.tags, request.tags)
            eventLocationResolver.apply(it, request.location)
            applyRecurrence(it, request.recurrence)
            val savedEvent = eventRepository.save(it)
            addHostParticipant(savedEvent, currentUserId)
            return savedEvent.toDto()
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
                if (this.recurrenceFrequency != RecurrenceFrequency.NONE) {
                    applyRecurrencePause(this, request.recurrencePausedUntil)
                } else {
                    this.recurrencePausedUntil = null
                }
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
        blacklistedIds: List<UUID>?,
        interestGroupId: UUID?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double,
        locationLabel: String?,
        match: Boolean,
        schedule: Boolean,
        jwt: Jwt?,
        pageable: Pageable,
    ): Page<EventResponse> {
        if (match || schedule) {
            val currentUserId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) }
                ?: throw InvalidAuthenticationException()
            return findPersonalized(
                tagIds,
                blacklistedIds,
                interestGroupId,
                latitude,
                longitude,
                radiusKm,
                locationLabel,
                schedule,
                currentUserId,
                pageable,
            )
        }

        if (latitude != null && longitude != null) {
            val radiusMeters = radiusKm * 1000
            return paginateDiscoverable(
                eventRepository.findNearby(latitude, longitude, radiusMeters, browseFetchPageable(pageable)).content,
                interestGroupId,
                pageable,
            ).map { it.toDto() }
        }
        val trimmedName = name?.trim().orEmpty()
        val trimmedDescription = description?.trim().orEmpty()
        val hasText = trimmedName.isNotEmpty() || trimmedDescription.isNotEmpty()
        val hasTagIds = !tagIds.isNullOrEmpty()
        val fetchPageable = browseFetchPageable(pageable)
        val events = when {
            !hasText && !hasTagIds ->
                eventRepository.findAll(fetchPageable)

            !hasText && hasTagIds ->
                eventRepository.findByTagIdIn(tagIds, fetchPageable)

            else ->
                eventRepository.search(
                    trimmedName,
                    trimmedDescription,
                    hasTagIds,
                    tagIds.orEmpty(),
                    fetchPageable,
                )
        }
        return paginateDiscoverable(events.content, interestGroupId, pageable).map { it.toDto() }
    }

    private fun browseFetchPageable(pageable: Pageable): Pageable {
        return PageRequest.of(0, maxOf(pageable.pageSize * 10, 200))
    }

    private fun paginateDiscoverable(
        candidates: List<Event>,
        interestGroupId: UUID?,
        pageable: Pageable,
    ): Page<Event> {
        val now = LocalDateTime.now()
        val filtered = candidates.filter {
            isDiscoverable(it, interestGroupId) && isVisibleInBrowseList(it, now)
        }
        val paged = filtered
            .drop(pageable.offset.toInt())
            .take(pageable.pageSize)
        return PageImpl(paged, pageable, filtered.size.toLong())
    }

    private fun findPersonalized(
        tagIds: List<UUID>?,
        blacklistedIds: List<UUID>?,
        interestGroupId: UUID?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double,
        locationLabel: String?,
        schedule: Boolean,
        currentUserId: UUID,
        pageable: Pageable,
    ): Page<EventResponse> {
        val tagIds = tagIds.orEmpty().toSet()
        val preferences = SmartMatchPreferences(
            tagIds = tagIds,
            blacklistedIds = blacklistedIds.orEmpty().toSet(),
            locationLabel = locationLabel?.trim()?.takeIf { it.isNotEmpty() },
        )
        val now = LocalDateTime.now()
        val fetchSize = maxOf(pageable.pageSize * 10, 200)
        val candidates = if (schedule) {
            findScheduleCandidates(
                tagIds,
                latitude,
                longitude,
                radiusKm,
                locationLabel,
                PageRequest.of(0, fetchSize),
            )
        } else {
            findSmartMatchCandidates(tagIds, PageRequest.of(0, fetchSize))
        }
        val busyEvents = if (schedule) findUpcomingCommitments(currentUserId, now) else emptyList()
        val ranked = smartMatchEventRanker.rank(
            candidates = candidates,
            preferences = preferences,
            now = now,
            schedule = schedule,
            busyEvents = busyEvents,
            isDiscoverable = { event -> isDiscoverable(event, interestGroupId) },
        )
        val matched = ranked
            .drop(pageable.offset.toInt())
            .take(pageable.pageSize)
            .map { rankedEvent -> rankedEvent.event.toDto() }

        return PageImpl(matched, pageable, ranked.size.toLong())
    }

    private fun findSmartMatchCandidates(
        tagIds: Set<UUID>,
        pageable: Pageable,
    ): List<Event> {
        val browsePool = { eventRepository.findAll(pageable).content }
        if (tagIds.isEmpty()) {
            return browsePool()
        }
        val expandedTagIds = smartMatchEventRanker.expandTagWeights(
            SmartMatchPreferences(tagIds = tagIds),
        ).keys
        val tagFilter = if (expandedTagIds.isEmpty()) tagIds else expandedTagIds
        val tagged = eventRepository.findByTagIdIn(tagFilter, pageable).content
        if (tagged.isEmpty()) {
            return browsePool()
        }
        return tagged
    }

    private fun findScheduleCandidates(
        tagIds: Set<UUID>,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double,
        locationLabel: String?,
        pageable: Pageable,
    ): List<Event> {
        val tagBased = findSmartMatchCandidates(tagIds, pageable)
        if (latitude != null && longitude != null) {
            val radiusMeters = radiusKm * 1000
            val nearby = eventRepository.findNearby(latitude, longitude, radiusMeters, pageable).content
            return (tagBased + nearby).distinctBy { it.id }.take(pageable.pageSize)
        }
        val normalizedLocationLabel = locationLabel?.trim()?.lowercase()
        if (normalizedLocationLabel.isNullOrBlank()) {
            return tagBased
        }

        val locationBased = eventRepository.findAll(pageable).content
            .filter { event -> matchesLocationLabel(event, normalizedLocationLabel) }
        return (tagBased + locationBased).distinctBy { it.id }.take(pageable.pageSize)
    }

    private fun findUpcomingCommitments(userId: UUID, from: LocalDateTime): List<Event> {
        val activeReservationStatuses = listOf(
            ReservationStatus.ACCEPTED,
            ReservationStatus.WHITELIST,
            ReservationStatus.PENDING,
        )
        val participantEvents = eventRepository.findUpcomingForParticipant(userId, from)
        val reservationEvents = eventRepository.findUpcomingForReservation(
            userId,
            from,
            activeReservationStatuses,
        )
        return (participantEvents + reservationEvents).distinctBy { it.id }
    }

    private fun matchesLocationLabel(event: Event, userLocationLabel: String): Boolean {
        val eventLocations = listOfNotNull(event.city, event.locationLabel, event.addressLine, event.postcode)
            .joinToString(" ")
            .lowercase()
        if (eventLocations.isBlank()) return false
        return eventLocations.contains(userLocationLabel) ||
                userLocationLabel.contains(eventLocations.substringBefore(",").trim())
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

    private fun applyRecurrence(event: Event, recurrence: EventRecurrenceDto?) {
        if (recurrence == null) {
            event.recurrenceFrequency = RecurrenceFrequency.NONE
            event.recurrenceDaysMask = 0
            event.recurrencePausedUntil = null
            return
        }
        event.recurrenceFrequency = recurrence.frequency
        event.recurrenceDaysMask = RecurrenceDaysMask.fromDayOfWeekSet(recurrence.daysOfWeek).bits
        event.recurrencePausedUntil = recurrence.pausedUntil
    }

    private fun applyRecurrencePause(event: Event, pausedUntil: LocalDateTime?) {
        RecurringEventVisibility.validatePause(event, pausedUntil)
        event.recurrencePausedUntil = pausedUntil
    }

    private fun addHostParticipant(event: Event, hostUserId: UUID) {
        val eventId = event.id ?: return
        val participant = EventParticipant(
            EventParticipantId(
                eventId = eventId,
                userId = hostUserId,
            ),
        )
        participant.event = event
        event.participants.add(participant)
    }

    private fun validateSchedule(startTime: LocalDateTime, endTime: LocalDateTime) {
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

    private fun isVisibleInBrowseList(event: Event, now: LocalDateTime): Boolean {
        return RecurringEventVisibility.isListable(event, now)
    }
}
