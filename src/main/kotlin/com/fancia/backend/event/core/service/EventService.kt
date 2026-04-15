package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.external.CommonServiceClient
import com.fancia.backend.event.mapper.EventMapper
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.event.core.dto.CreateEventRequest
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventRequest
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
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
    private val commonServiceClient: CommonServiceClient
) {
    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): Event? {
        return eventRepository.findByIdAndCreatedBy(id, createdBy)
    }

    @Transactional
    fun create(request: @Valid CreateEventRequest, jwt: Jwt): EventResponse {
        val requesterId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        eventMapper.toBean(request).let {
            it.createdBy = requesterId
            val createdBy = EventParticipant(userId = requesterId)
            createdBy.event = it
            it.participants.add(createdBy)
            val response = commonServiceClient.getTags(request.tags)
            it.tags.clear()
            it.tags.addAll(response.map { t -> t.name })
            return eventRepository.save(it).let(eventMapper::toDto)
        }
    }

    @Transactional
    fun update(id: UUID, request: @Valid UpdateEventRequest, jwt: Jwt): EventResponse {
        val requesterId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val event = findByIdAndCreatedBy(id, requesterId) ?: throw EventNotFoundException(id)
        return eventRepository.save(eventMapper.toBean(request, event)).let(eventMapper::toDto)
    }

    fun findAll(
        name: String?,
        description: String?,
        tags: String?,
        pageable: Pageable
    ): Page<EventResponse> {
        val groups = when {
            name.isNullOrBlank() && description.isNullOrBlank() && tags.isNullOrBlank() ->
                eventRepository.findAll(pageable)

            else -> {
                eventRepository.findAll(
                    name?.trim() ?: "",
                    description?.trim() ?: "",
                    tags?.trim() ?: "",
                    pageable
                )
            }
        }
        return groups.map(eventMapper::toDto)
    }
}