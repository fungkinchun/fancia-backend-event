package com.fancia.backend.event.core.service

import tools.jackson.core.type.TypeReference
import com.fancia.backend.shared.common.redis.CacheKeys
import com.fancia.backend.shared.common.redis.CachedPage
import com.fancia.backend.shared.common.redis.RedisQueryCache
import com.fancia.backend.shared.event.core.entity.Event
import com.fancia.backend.shared.event.core.entity.EventTimeSlot
import com.fancia.backend.event.core.repository.EventOccurrenceRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.repository.ReservationRepository
import com.fancia.backend.event.core.support.BusyOccurrence
import com.fancia.backend.shared.event.core.support.EventTimeSlotSchedule
import com.fancia.backend.shared.event.core.support.RecurringEventVisibility
import com.fancia.backend.event.core.support.SmartMatchEventRanker
import com.fancia.backend.event.core.support.SmartMatchPreferences
import com.fancia.backend.event.external.CommonServiceClient
import com.fancia.backend.event.external.UserServiceClient
import com.fancia.backend.event.mapper.toEntity
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.core.exception.PremiumFeatureLimitException
import com.fancia.backend.shared.common.core.utils.InviteTokens
import com.fancia.backend.shared.common.core.utils.Slugify
import com.fancia.backend.shared.common.tag.core.dto.CreateTagsRequest
import com.fancia.backend.shared.common.tag.core.dto.TagItemRequest
import com.fancia.backend.shared.event.core.dto.CreateEventRequest
import com.fancia.backend.shared.event.core.dto.EventRecurrenceDto
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventRequest
import com.fancia.backend.shared.event.core.enums.EventType
import com.fancia.backend.shared.event.core.enums.EventVisibility
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import com.fancia.backend.shared.event.core.enums.ReservationStatus
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import com.fancia.backend.shared.event.core.exception.GroupEventRequiresInterestGroupsException
import com.fancia.backend.shared.event.core.model.RecurrenceDaysMask
import com.fancia.backend.shared.user.core.support.PremiumLimits
import com.fancia.backend.shared.user.core.support.isPremiumClaim
import jakarta.validation.Valid
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val eventOccurrenceRepository: EventOccurrenceRepository,
    private val reservationRepository: ReservationRepository,
    private val eventOccurrenceService: EventOccurrenceService,
    private val eventTicketTierService: EventTicketTierService,
    private val commonServiceClient: CommonServiceClient,
    private val userServiceClient: UserServiceClient,
    private val eventLocationResolver: EventLocationResolver,
    private val smartMatchEventRanker: SmartMatchEventRanker,
    private val blockedResourceService: BlockedResourceService,
    private val savedResourceService: SavedResourceService,
    private val redisQueryCache: ObjectProvider<RedisQueryCache>,
) {
    fun listSavedEvents(jwt: Jwt, pageable: Pageable): Page<EventResponse> {
        val page = savedResourceService.listSavedPage(jwt, pageable)
        if (page.isEmpty) {
            return PageImpl(emptyList(), pageable, 0)
        }
        val ids = page.content.map { it.id.resourceId }
        val eventsById = eventRepository.findAllById(ids).associateBy { it.id }
        val now = LocalDateTime.now()
        val responses = ids.mapNotNull { id ->
            val event = eventsById[id] ?: return@mapNotNull null
            eventOccurrenceService.toUpcomingResponse(event, now).also {
                it.savedByCurrentUser = true
            }
        }
        return PageImpl(responses, pageable, page.totalElements)
    }

    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): Event? {
        return eventRepository.findByIdAndCreatedBy(id, createdBy)
    }

    fun findByIdOrSlug(ref: String, jwt: Jwt? = null, invite: String? = null): EventResponse {
        val event = syncInviteToken(resolveByIdOrSlug(ref))
        assertCanAccess(event, jwt, invite)
        val response = eventOccurrenceService.toUpcomingResponse(event, LocalDateTime.now())
        enrichSaved(response, jwt)
        exposeInviteTokenIfCreator(response, event, jwt)
        return response
    }

    fun findById(id: UUID, jwt: Jwt? = null, invite: String? = null): EventResponse {
        val event = syncInviteToken(
            eventRepository.findById(id).orElseThrow { EventNotFoundException(id) },
        )
        assertCanAccess(event, jwt, invite)
        val response = eventOccurrenceService.toUpcomingResponse(event, LocalDateTime.now())
        enrichSaved(response, jwt)
        exposeInviteTokenIfCreator(response, event, jwt)
        return response
    }

    private fun enrichSaved(response: EventResponse, jwt: Jwt?) {
        val userId = jwt?.getClaimAsString("userId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val eventId = response.id
        if (userId == null || eventId == null) {
            response.savedByCurrentUser = null
            return
        }
        response.savedByCurrentUser = savedResourceService.isSaved(userId, eventId)
    }

    fun resolveByIdOrSlug(ref: String): Event {
        val trimmed = ref.trim()
        if (trimmed.isEmpty()) throw EventNotFoundException(ref)
        val asUuid = runCatching { UUID.fromString(trimmed) }.getOrNull()
        if (asUuid != null) {
            return eventRepository.findById(asUuid).orElseThrow { EventNotFoundException(asUuid) }
        }
        return eventRepository.findBySlug(trimmed).orElseThrow { EventNotFoundException(trimmed) }
    }

    @Transactional
    fun create(request: @Valid CreateEventRequest, jwt: Jwt): EventResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val visibility = request.visibility ?: EventVisibility.PUBLIC
        validateVisibility(visibility, request.interestGroups)
        val scheduleWindows = EventTimeSlotSchedule.resolve(
            request.startTime,
            request.endTime,
            request.timeSlots,
        )
        request.recurrence?.let { RecurringEventVisibility.validateRecurrence(it) }
        if (visibility == EventVisibility.PRIVATE &&
            !PremiumLimits.allowsUnlimitedPrivateEvents(jwt.isPremiumClaim())
        ) {
            val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay()
            val hosted = eventRepository.countByCreatedByAndVisibilitySince(
                currentUserId,
                EventVisibility.PRIVATE,
                monthStart,
            )
            if (hosted >= PremiumLimits.PRIVATE_EVENTS_PER_MONTH_FREE) {
                throw PremiumFeatureLimitException(
                    "Free plan allows up to ${PremiumLimits.PRIVATE_EVENTS_PER_MONTH_FREE} private events per month. " +
                        "Upgrade to Fancia Premium for unlimited private event hosting.",
                )
            }
        }
        request.toEntity().let {
            it.createdBy = currentUserId
            it.visibility = visibility
            it.slug = allocateEventSlug(request.name)
            applyTags(it.tags, request.tags)
            eventLocationResolver.apply(it, request.location)
            applyRecurrence(it, request.recurrence)
            syncInviteTokenInPlace(it)
            val savedEvent = eventRepository.save(it)
            replaceTimeSlots(savedEvent, scheduleWindows, currentUserId)
            syncDenormalizedSchedule(savedEvent)
            eventRepository.save(savedEvent)
            savedEvent.timeSlots.sortedBy { slot -> slot.sortOrder }.forEach { slot ->
                eventOccurrenceService.createInitialOccurrence(
                    savedEvent,
                    slot.startTime,
                    slot.endTime,
                    currentUserId,
                    slot,
                )
            }
            request.ticketTiers
                ?.takeIf { tiers -> tiers.isNotEmpty() }
                ?.let { tiers ->
                    eventTicketTierService.persistTiers(
                        savedEvent,
                        tiers,
                        currentUserId,
                        jwt.isPremiumClaim(),
                    )
                }
            invalidateEventCaches()
            return eventOccurrenceService.toUpcomingResponse(savedEvent, LocalDateTime.now()).also {
                exposeInviteTokenIfCreator(it, savedEvent, jwt)
            }
        }
    }

    @Transactional
    fun update(id: UUID, request: @Valid UpdateEventRequest, jwt: Jwt): EventResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val event = findByIdAndCreatedBy(id, currentUserId) ?: throw EventNotFoundException(id)
        val visibility = request.visibility ?: event.visibility
        validateVisibility(visibility, event.interestGroups)
        val scheduleWindows = EventTimeSlotSchedule.resolve(
            request.startTime,
            request.endTime,
            request.timeSlots,
        )
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
                replaceTimeSlots(this, scheduleWindows, currentUserId)
                syncDenormalizedSchedule(this)
                syncInviteTokenInPlace(this)
            },
        ).let { updated ->
            updated.timeSlots.sortedBy { slot -> slot.sortOrder }.forEach { slot ->
                val eventId = updated.id ?: return@forEach
                if (!eventOccurrenceRepository.existsByEventIdAndStartTime(eventId, slot.startTime)) {
                    eventOccurrenceService.createInitialOccurrence(
                        updated,
                        slot.startTime,
                        slot.endTime,
                        currentUserId,
                        slot,
                    )
                }
            }
            invalidateEventCaches()
            eventOccurrenceService.toUpcomingResponse(updated, LocalDateTime.now()).also {
                exposeInviteTokenIfCreator(it, updated, jwt)
            }
        }
    }

    @Transactional
    fun removeTagFromAllEvents(tagId: UUID) {
        val eventsWithTag = eventRepository.findByTagId(tagId)
        for (event in eventsWithTag) {
            event.tags.remove(tagId)
        }
        if (eventsWithTag.isNotEmpty()) {
            eventRepository.saveAll(eventsWithTag)
            invalidateEventCaches()
        }
    }

    fun findAll(
        name: String?,
        description: String?,
        tagIds: List<UUID>?,
        interestGroupId: UUID?,
        eventType: EventType?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double,
        locationLabel: String?,
        match: Boolean,
        schedule: Boolean,
        userId: UUID?,
        past: Boolean,
        jwt: Jwt?,
        pageable: Pageable,
    ): Page<EventResponse> {
        if (userId != null) {
            val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) }
            if (!canViewUserEvents(userId, viewerId)) {
                return PageImpl(emptyList(), pageable, 0)
            }
            val now = LocalDateTime.now()
            val involved = eventOccurrenceRepository.findEventsByUserInvolvement(userId, Pageable.unpaged())
                .content
                .filter { matchesEventType(it, eventType) }
            val paged = involved
                .drop(pageable.offset.toInt())
                .take(pageable.pageSize)
            return PageImpl(
                paged.map { event -> eventOccurrenceService.toUpcomingResponse(event, now) },
                pageable,
                involved.size.toLong(),
            )
        }

        if (match || schedule) {
            val currentUserId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) }
                ?: throw InvalidAuthenticationException()
            return cachedPersonalized(
                tagIds,
                interestGroupId,
                eventType,
                latitude,
                longitude,
                radiusKm,
                locationLabel,
                schedule,
                currentUserId,
                pageable,
            )
        }

        val cache = redisQueryCache.ifAvailable
        if (cache != null) {
            val key = browseCacheKey(
                name, description, tagIds, interestGroupId, eventType,
                latitude, longitude, radiusKm, past, pageable,
            )
            val cached = cache.getOrLoad(
                key,
                BROWSE_TTL,
                object : TypeReference<CachedPage<EventResponse>>() {},
            ) {
                CachedPage.from(
                    loadPublicBrowse(
                        name, description, tagIds, interestGroupId, eventType,
                        latitude, longitude, radiusKm, past, pageable,
                    ),
                )
            }
            return cached.toPage(pageable)
        }
        return loadPublicBrowse(
            name, description, tagIds, interestGroupId, eventType,
            latitude, longitude, radiusKm, past, pageable,
        )
    }

    private fun cachedPersonalized(
        tagIds: List<UUID>?,
        interestGroupId: UUID?,
        eventType: EventType?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double,
        locationLabel: String?,
        schedule: Boolean,
        currentUserId: UUID,
        pageable: Pageable,
    ): Page<EventResponse> {
        val cache = redisQueryCache.ifAvailable
        if (cache == null) {
            return findPersonalized(
                tagIds, interestGroupId, eventType, latitude, longitude,
                radiusKm, locationLabel, schedule, currentUserId, pageable,
            )
        }
        val key = "$MATCH_PREFIX$currentUserId:" + CacheKeys.hash(
            tagIds, interestGroupId, eventType, latitude, longitude,
            radiusKm, locationLabel, schedule, pageable.pageNumber, pageable.pageSize,
        )
        val cached = cache.getOrLoad(
            key,
            MATCH_TTL,
            object : TypeReference<CachedPage<EventResponse>>() {},
        ) {
            CachedPage.from(
                findPersonalized(
                    tagIds, interestGroupId, eventType, latitude, longitude,
                    radiusKm, locationLabel, schedule, currentUserId, pageable,
                ),
            )
        }
        return cached.toPage(pageable)
    }

    private fun loadPublicBrowse(
        name: String?,
        description: String?,
        tagIds: List<UUID>?,
        interestGroupId: UUID?,
        eventType: EventType?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double,
        past: Boolean,
        pageable: Pageable,
    ): Page<EventResponse> {
        if (latitude != null && longitude != null) {
            val radiusMeters = radiusKm * 1000
            val now = LocalDateTime.now()
            return paginateDiscoverable(
                eventRepository.findNearby(latitude, longitude, radiusMeters, browseFetchPageable(pageable)).content,
                interestGroupId,
                eventType,
                pageable,
                past,
            ).map { event -> toBrowseDto(event, now, past) }
        }
        val trimmedName = name?.trim().orEmpty()
        val trimmedDescription = description?.trim().orEmpty()
        val hasText = trimmedName.isNotEmpty() || trimmedDescription.isNotEmpty()
        val hasTagIds = !tagIds.isNullOrEmpty()
        val fetchPageable = browseFetchPageable(pageable)
        val now = LocalDateTime.now()
        val events = when {
            past && !hasText && !hasTagIds ->
                eventRepository.findStartedBefore(
                    now,
                    PageRequest.of(
                        fetchPageable.pageNumber,
                        fetchPageable.pageSize,
                        Sort.by(Sort.Direction.DESC, "startTime"),
                    ),
                )

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
        return paginateDiscoverable(events.content, interestGroupId, eventType, pageable, past)
            .map { event -> toBrowseDto(event, now, past) }
    }

    private fun browseCacheKey(
        name: String?,
        description: String?,
        tagIds: List<UUID>?,
        interestGroupId: UUID?,
        eventType: EventType?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double,
        past: Boolean,
        pageable: Pageable,
    ): String =
        BROWSE_PREFIX + CacheKeys.hash(
            name?.trim(), description?.trim(), tagIds, interestGroupId, eventType,
            latitude, longitude, radiusKm, past, pageable.pageNumber, pageable.pageSize,
        )

    private fun invalidateEventCaches() {
        val cache = redisQueryCache.ifAvailable ?: return
        cache.evictByPrefix(BROWSE_PREFIX)
        cache.evictByPrefix(MATCH_PREFIX)
    }

    companion object {
        private const val BROWSE_PREFIX = "event:browse:"
        private const val MATCH_PREFIX = "event:match:"
        private val BROWSE_TTL = Duration.ofSeconds(60)
        private val MATCH_TTL = Duration.ofSeconds(30)
    }

    private fun toBrowseDto(event: Event, now: LocalDateTime, past: Boolean): EventResponse {
        return if (past) {
            eventOccurrenceService.toPastResponse(event, now)
        } else {
            eventOccurrenceService.toUpcomingResponse(event, now)
        }
    }

    private fun browseFetchPageable(pageable: Pageable): Pageable {
        return PageRequest.of(0, maxOf(pageable.pageSize * 10, 200))
    }

    private fun paginateDiscoverable(
        candidates: List<Event>,
        interestGroupId: UUID?,
        eventType: EventType?,
        pageable: Pageable,
        past: Boolean = false,
    ): Page<Event> {
        val now = LocalDateTime.now()
        val filtered = candidates.filter {
            isDiscoverable(it, interestGroupId) &&
                matchesEventType(it, eventType) &&
                if (past) {
                    RecurringEventVisibility.isPastListable(it, now)
                } else {
                    isVisibleInBrowseList(it, now)
                }
        }.let { list ->
            if (past) {
                list.sortedByDescending { it.startTime }
            } else {
                list
            }
        }
        val paged = filtered
            .drop(pageable.offset.toInt())
            .take(pageable.pageSize)
        return PageImpl(paged, pageable, filtered.size.toLong())
    }

    private fun matchesEventType(event: Event, eventType: EventType?): Boolean =
        eventType == null || event.eventType == eventType

    private fun findPersonalized(
        tagIds: List<UUID>?,
        interestGroupId: UUID?,
        eventType: EventType?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double,
        locationLabel: String?,
        schedule: Boolean,
        currentUserId: UUID,
        pageable: Pageable,
    ): Page<EventResponse> {
        val tagIds = tagIds.orEmpty().toSet()
        val (blockedUserIds, blockedTagIds) = blockedResourceService.loadUserAndTagBlocks(currentUserId)
        val preferences = SmartMatchPreferences(
            tagIds = tagIds,
            blockedUserIds = blockedUserIds,
            blockedTagIds = blockedTagIds,
            blockedEventIds = blockedResourceService.blockedEventIds(currentUserId),
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
        val busyOccurrences = if (schedule) findUpcomingCommitments(currentUserId, now) else emptyList()
        val ranked = smartMatchEventRanker.rank(
            candidates = candidates,
            preferences = preferences,
            now = now,
            schedule = schedule,
            busyOccurrences = busyOccurrences,
            isDiscoverable = { event ->
                isDiscoverable(event, interestGroupId) && matchesEventType(event, eventType)
            },
        )
        val matched = ranked
            .drop(pageable.offset.toInt())
            .take(pageable.pageSize)
            .map { rankedEvent ->
                eventOccurrenceService.toUpcomingResponse(rankedEvent.event, now)
            }

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
        if (latitude != null && longitude != null) {
            val radiusMeters = radiusKm * 1000
            val nearby = eventRepository.findNearby(latitude, longitude, radiusMeters, pageable).content
            val tagBased = if (tagIds.isEmpty()) emptyList() else findSmartMatchCandidates(tagIds, pageable)
            return (tagBased + nearby).distinctBy { it.id }.take(pageable.pageSize)
        }
        val normalizedLocationLabel = locationLabel?.trim()?.lowercase()
        if (!normalizedLocationLabel.isNullOrBlank()) {
            val locationBased = eventRepository.findAll(pageable).content
                .filter { event -> matchesLocationLabel(event, normalizedLocationLabel) }
            val tagBased = if (tagIds.isEmpty()) {
                emptyList()
            } else {
                findSmartMatchCandidates(tagIds, pageable)
                    .filter { event -> matchesLocationLabel(event, normalizedLocationLabel) }
            }
            return (tagBased + locationBased).distinctBy { it.id }.take(pageable.pageSize)
        }
        return findSmartMatchCandidates(tagIds, pageable)
    }

    private fun findUpcomingCommitments(userId: UUID, from: LocalDateTime): List<BusyOccurrence> {
        val activeReservationStatuses = listOf(
            ReservationStatus.PENDING,
            ReservationStatus.PAID,
            ReservationStatus.ACCEPTED,
            ReservationStatus.WHITELIST,
        )
        val participantOccurrences = eventOccurrenceRepository.findUpcomingForParticipant(userId, from)
        val reservationOccurrences = eventOccurrenceRepository.findUpcomingForReservation(
            userId,
            from,
            activeReservationStatuses,
        )
        return (participantOccurrences + reservationOccurrences)
            .distinctBy { it.id }
            .map { occurrence ->
                BusyOccurrence(
                    eventId = occurrence.event?.id ?: error("Occurrence missing event"),
                    startTime = occurrence.startTime,
                    endTime = occurrence.endTime,
                )
            }
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

    private fun allocateEventSlug(name: String): String =
        Slugify.allocateUnique(name, fallback = "event") { eventRepository.existsBySlug(it) }

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

    private fun isVisibleInBrowseList(event: Event, now: LocalDateTime): Boolean {
        return RecurringEventVisibility.isListable(event, now)
    }

    private fun replaceTimeSlots(
        event: Event,
        windows: List<EventTimeSlotSchedule.Window>,
        userId: UUID,
    ) {
        val existing = event.timeSlots.sortedBy { it.sortOrder }.toMutableList()
        windows.forEachIndexed { index, window ->
            if (index < existing.size) {
                existing[index].apply {
                    startTime = window.startTime
                    endTime = window.endTime
                    sortOrder = index
                }
            } else {
                event.timeSlots.add(
                    EventTimeSlot().apply {
                        this.event = event
                        this.startTime = window.startTime
                        this.endTime = window.endTime
                        this.sortOrder = index
                        this.createdBy = userId
                    },
                )
            }
        }
        if (existing.size > windows.size) {
            event.timeSlots.removeAll(existing.drop(windows.size).toSet())
        }
    }

    private fun syncDenormalizedSchedule(event: Event) {
        val first = event.timeSlots.minWithOrNull(compareBy({ it.sortOrder }, { it.startTime })) ?: return
        event.startTime = first.startTime
        event.endTime = first.endTime
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

    private fun canViewUserEvents(targetUserId: UUID, viewerId: UUID?): Boolean {
        if (viewerId == targetUserId) return true
        val user = runCatching { userServiceClient.getUser(targetUserId) }.getOrNull() ?: return false
        return user.eventsCount != null
    }

    private fun assertCanAccess(event: Event, jwt: Jwt?, invite: String?) {
        if (event.visibility != EventVisibility.PRIVATE) return
        val userId = jwtUserId(jwt)
        if (userId != null && userId == event.createdBy) return
        val token = event.inviteToken
        if (!token.isNullOrBlank() && !invite.isNullOrBlank() && token == invite) return
        val eventId = event.id
        if (userId != null && eventId != null &&
            reservationRepository.existsByEventIdAndUserId(eventId, userId)
        ) {
            return
        }
        throw EventNotFoundException(event.id ?: event.slug)
    }

    fun assertCanJoin(event: Event, jwt: Jwt, invite: String?) {
        assertCanAccess(event, jwt, invite)
    }

    private fun syncInviteToken(event: Event): Event {
        if (!syncInviteTokenInPlace(event)) return event
        return eventRepository.save(event)
    }

    private fun syncInviteTokenInPlace(event: Event): Boolean {
        if (event.visibility == EventVisibility.PRIVATE) {
            if (event.inviteToken.isNullOrBlank()) {
                event.inviteToken = InviteTokens.generate()
                return true
            }
            return false
        }
        if (event.inviteToken != null) {
            event.inviteToken = null
            return true
        }
        return false
    }

    private fun exposeInviteTokenIfCreator(response: EventResponse, event: Event, jwt: Jwt?) {
        val userId = jwtUserId(jwt)
        if (userId != null && userId == event.createdBy && event.visibility == EventVisibility.PRIVATE) {
            response.inviteToken = event.inviteToken
        } else {
            response.inviteToken = null
        }
    }

    private fun jwtUserId(jwt: Jwt?): UUID? =
        jwt?.getClaimAsString("userId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}
