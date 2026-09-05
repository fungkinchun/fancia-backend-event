package com.fancia.backend.event.mapper

import com.fancia.backend.shared.event.core.entity.Event
import com.fancia.backend.shared.event.core.entity.EventOccurrence
import com.fancia.backend.shared.event.core.entity.EventParticipant
import com.fancia.backend.shared.event.core.entity.EventTimeSlot
import com.fancia.backend.event.core.support.EventLocationSupport
import com.fancia.backend.shared.common.social.core.dto.LinkItem
import com.fancia.backend.shared.common.social.core.dto.LinkResponse
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.event.core.dto.*
import com.fancia.backend.shared.event.core.enums.EventType
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import com.fancia.backend.shared.event.core.model.RecurrenceDaysMask

fun Event.toDto(nextOccurrence: EventOccurrence? = null): EventResponse =
    EventResponse(
        id = this@toDto.id,
        name = this@toDto.name,
        slug = this@toDto.slug,
        description = this@toDto.description,
        interestGroups = this@toDto.interestGroups,
        createdBy = this@toDto.createdBy,
        createdAt = this@toDto.createdAt,
        startTime = nextOccurrence?.startTime ?: this@toDto.startTime,
        endTime = nextOccurrence?.endTime ?: this@toDto.endTime,
        tags = this@toDto.tags,
        eventType = this@toDto.eventType,
        visibility = this@toDto.visibility,
        location = EventLocationSupport.toDto(this),
        links = this@toDto.links.map { it.toDto() }.toSet(),
        recurrence = toRecurrenceDto(this@toDto),
        approvalRequired = this@toDto.approvalRequired,
        timeSlots = this@toDto.timeSlots
            .sortedBy { it.sortOrder }
            .mapNotNull { it.toDto() },
    )

fun CreateEventRequest.toEntity(): Event =
    Event().apply {
        name = this@toEntity.name
        description = this@toEntity.description
        startTime = this@toEntity.startTime
        endTime = this@toEntity.endTime
        interestGroups = this@toEntity.interestGroups.toMutableSet()
        links = this@toEntity.links.map { it.toEntity() }.toMutableSet()
        eventType = this@toEntity.eventType ?: EventType.REGULAR
        approvalRequired = this@toEntity.approvalRequired ?: true
    }

fun UpdateEventRequest.toEntity(event: Event): Event {
    event.name = this@toEntity.name
    event.description = this@toEntity.description
    this@toEntity.startTime?.let { event.startTime = it }
    this@toEntity.endTime?.let { event.endTime = it }
    event.links.clear()
    event.links.addAll(this@toEntity.links.map { it.toEntity() })
    this@toEntity.eventType?.let { event.eventType = it }
    this@toEntity.approvalRequired?.let { event.approvalRequired = it }
    return event
}

fun EventResponse.toEntity(): Event =
    Event().apply {
        id = this@toEntity.id
        name = this@toEntity.name
        slug = this@toEntity.slug
        description = this@toEntity.description
        interestGroups = this@toEntity.interestGroups.toMutableSet()
        createdBy = this@toEntity.createdBy
        createdAt = this@toEntity.createdAt
        startTime = this@toEntity.startTime
        endTime = this@toEntity.endTime
        tags = this@toEntity.tags.toMutableSet()
        eventType = this@toEntity.eventType
        visibility = this@toEntity.visibility
        links = this@toEntity.links.map { it.toEntity() }.toMutableSet()
        approvalRequired = this@toEntity.approvalRequired
        EventLocationSupport.applyFromDto(this, this@toEntity.location)
    }

fun EventParticipant.toDto(): EventParticipantResponse =
    EventParticipantResponse(
        userId = this@toDto.id.userId,
        role = this@toDto.role,
    )

private fun Link.toDto(): LinkResponse =
    LinkResponse(type = this@toDto.type, url = this@toDto.url)

private fun LinkItem.toEntity(): Link =
    Link(type = this@toEntity.type, url = this@toEntity.url)

private fun LinkResponse.toEntity(): Link =
    Link(type = this@toEntity.type, url = this@toEntity.url)

private fun toRecurrenceDto(event: Event): EventRecurrenceDto? {
    if (event.recurrenceFrequency == RecurrenceFrequency.NONE) return null
    return EventRecurrenceDto(
        frequency = event.recurrenceFrequency,
        daysOfWeek = RecurrenceDaysMask(event.recurrenceDaysMask).toDayOfWeekSet(),
        pausedUntil = event.recurrencePausedUntil,
    )
}

private fun EventTimeSlot.toDto(): EventTimeSlotResponse? {
    val slotId = id ?: return null
    return EventTimeSlotResponse(
        id = slotId,
        startTime = startTime,
        endTime = endTime,
        sortOrder = sortOrder,
    )
}
