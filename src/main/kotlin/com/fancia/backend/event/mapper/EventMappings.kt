package com.fancia.backend.event.mapper

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.event.core.support.EventLocationSupport
import com.fancia.backend.shared.common.social.core.dto.LinkResponse
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.event.core.dto.CreateEventRequest
import com.fancia.backend.shared.event.core.dto.EventParticipantResponse
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventRequest

fun Event.toDto(): EventResponse =
    EventResponse(
        id = this@toDto.id,
        name = this@toDto.name,
        description = this@toDto.description,
        interestGroups = this@toDto.interestGroups,
        createdBy = this@toDto.createdBy,
        createdAt = this@toDto.createdAt,
        startTime = this@toDto.startTime,
        endTime = this@toDto.endTime,
        tags = this@toDto.tags,
        visibility = this@toDto.visibility,
        location = EventLocationSupport.toDto(this),
        links = this@toDto.links.map { it.toDto() }.toSet(),
    )

fun CreateEventRequest.toEntity(): Event =
    Event().apply {
        name = this@toEntity.name
        description = this@toEntity.description
        startTime = this@toEntity.startTime
        endTime = this@toEntity.endTime
        interestGroups = interestGroups.toMutableSet()
        visibility = this@toEntity.visibility!!
    }

fun UpdateEventRequest.toEntity(event: Event): Event {
    event.description = description
    event.startTime = startTime
    event.endTime = endTime
    return event
}

fun EventResponse.toEntity(): Event =
    Event().apply {
        id = this@toEntity.id
        name = this@toEntity.name
        description = this@toEntity.description
        interestGroups = interestGroups.toMutableSet()
        createdBy = this@toEntity.createdBy
        createdAt = this@toEntity.createdAt
        startTime = this@toEntity.startTime
        endTime = this@toEntity.endTime
        tags = tags.toMutableSet()
        visibility = this@toEntity.visibility
        links = links.map { Link(type = it.type, url = it.url) }.toMutableSet()
    }

fun EventParticipant.toDto(): EventParticipantResponse =
    EventParticipantResponse(
        userId = id.userId,
        role = role,
    )

private fun Link.toDto(): LinkResponse =
    LinkResponse(type = type, url = url)
