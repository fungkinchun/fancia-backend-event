package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.event.core.repository.EventParticipantRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.mapper.EventParticipantMapper
import com.fancia.backend.shared.event.core.dto.EventParticipantResponse
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.util.*

@Service
class EventParticipantService(
    private val eventParticipantRepository: EventParticipantRepository,
    private val eventRepository: EventRepository,
    private val eventParticipantMapper: EventParticipantMapper,
) {
    fun findParticipants(eventId: UUID, pageable: Pageable): Page<EventParticipantResponse> {
        if (!eventRepository.existsById(eventId)) {
            throw EventNotFoundException(eventId)
        }
        val participants: Page<EventParticipant> =
            eventParticipantRepository.findByEventId(eventId, pageable)

        return participants.map(eventParticipantMapper::toDto)
    }
}