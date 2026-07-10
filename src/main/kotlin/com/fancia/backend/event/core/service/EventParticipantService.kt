package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.EventOccurrenceRepository
import com.fancia.backend.event.core.repository.EventParticipantRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.mapper.toDto
import com.fancia.backend.shared.event.core.dto.EventParticipantResponse
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import com.fancia.backend.shared.event.core.exception.OccurrenceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class EventParticipantService(
    private val eventRepository: EventRepository,
    private val eventOccurrenceRepository: EventOccurrenceRepository,
    private val eventParticipantRepository: EventParticipantRepository,
) {
    fun findParticipants(
        eventId: UUID,
        occurrenceId: UUID,
        pageable: Pageable,
    ): Page<EventParticipantResponse> {
        eventRepository.findByIdOrNull(eventId) ?: throw EventNotFoundException(eventId)
        eventOccurrenceRepository.findByIdAndEventId(occurrenceId, eventId)
            ?: throw OccurrenceNotFoundException(eventId, occurrenceId)

        return eventParticipantRepository.findByIdOccurrenceId(occurrenceId, pageable)
            .map { it.toDto() }
    }
}
