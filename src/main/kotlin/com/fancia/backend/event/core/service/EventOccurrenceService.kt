package com.fancia.backend.event.core.service

import com.fancia.backend.shared.event.core.entity.Event
import com.fancia.backend.shared.event.core.entity.EventOccurrence
import com.fancia.backend.shared.event.core.entity.EventParticipant
import com.fancia.backend.shared.event.core.entity.EventParticipantId
import com.fancia.backend.shared.event.core.dto.EventOccurrenceResponse
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.enums.OccurrenceStatus
import com.fancia.backend.shared.event.core.exception.OccurrenceNotFoundException
import com.fancia.backend.event.core.repository.EventOccurrenceRepository
import com.fancia.backend.shared.event.core.support.RecurringEventVisibility
import com.fancia.backend.event.mapper.toDto
import com.fancia.backend.shared.event.core.enums.EventRole
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class EventOccurrenceService(
    private val eventOccurrenceRepository: EventOccurrenceRepository,
) {
    @Transactional
    fun createInitialOccurrence(
        event: Event,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        hostUserId: UUID,
    ): EventOccurrence {
        val occurrence = EventOccurrence().apply {
            this.event = event
            this.startTime = startTime
            this.endTime = endTime
            this.status = OccurrenceStatus.SCHEDULED
            this.createdBy = hostUserId
        }
        event.occurrences.add(occurrence)
        val saved = eventOccurrenceRepository.save(occurrence)
        addHostParticipant(saved, hostUserId)
        return saved
    }

    fun findByEventId(eventId: UUID, pageable: Pageable): Page<EventOccurrenceResponse> {
        return eventOccurrenceRepository.findByEventIdOrderByStartTimeAsc(eventId, pageable).map { it.toDto() }
    }

    fun getOccurrence(eventId: UUID, occurrenceId: UUID): EventOccurrence {
        return eventOccurrenceRepository.findByIdAndEventId(occurrenceId, eventId)
            ?: throw OccurrenceNotFoundException(eventId, occurrenceId)
    }

    /** Next persisted occurrence at or after [now], if any. Does not create rows. */
    fun findNextUpcoming(event: Event, now: LocalDateTime): EventOccurrence? {
        val eventId = event.id ?: return null
        return eventOccurrenceRepository.findFirstByEventIdAndStartTimeGreaterThanEqualAndStatusOrderByStartTimeAsc(
            eventId,
            now,
        )
    }

    /** Most recent persisted occurrence that started before [now], if any. */
    fun findLatestPast(event: Event, now: LocalDateTime): EventOccurrence? {
        val eventId = event.id ?: return null
        return eventOccurrenceRepository.findFirstByEventIdAndStartTimeLessThanAndStatusOrderByStartTimeDesc(
            eventId,
            now,
        )
    }

    /**
     * Maps an event for upcoming browse/detail.
     * Prefers a persisted upcoming occurrence; otherwise uses a computed next slot
     * for display times only (does not insert an occurrence row).
     */
    fun toUpcomingResponse(event: Event, now: LocalDateTime = LocalDateTime.now()): EventResponse {
        val next = findNextUpcoming(event, now)
        if (next != null) return event.toDto(next)

        val computedStart = RecurringEventVisibility.nextOccurrenceStart(event, now)
        val computedEnd = RecurringEventVisibility.nextOccurrenceEnd(event, now)
        val base = event.toDto(null)
        if (computedStart == null) return base
        return base.copy(startTime = computedStart, endTime = computedEnd)
    }

    fun toPastResponse(event: Event, now: LocalDateTime = LocalDateTime.now()): EventResponse {
        return event.toDto(findLatestPast(event, now))
    }

    private fun addHostParticipant(occurrence: EventOccurrence, hostUserId: UUID) {
        val occurrenceId = occurrence.id ?: return
        if (occurrence.participants.any { it.id.userId == hostUserId }) return
        val participant = EventParticipant(
            EventParticipantId(
                occurrenceId = occurrenceId,
                userId = hostUserId,
            ),
        )
        participant.occurrence = occurrence
        participant.role = EventRole.HOST
        occurrence.participants.add(participant)
    }
}
