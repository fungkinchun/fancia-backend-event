package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.entity.EventOccurrence
import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.event.core.entity.EventParticipantId
import com.fancia.backend.shared.event.core.dto.EventOccurrenceResponse
import com.fancia.backend.shared.event.core.enums.OccurrenceStatus
import com.fancia.backend.shared.event.core.exception.OccurrenceNotFoundException
import com.fancia.backend.event.core.repository.EventOccurrenceRepository
import com.fancia.backend.event.core.support.RecurringEventVisibility
import com.fancia.backend.event.mapper.toDto
import com.fancia.backend.shared.event.core.enums.EventRole
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
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

    fun findNextUpcoming(event: Event, now: LocalDateTime): EventOccurrence? {
        ensureUpcomingOccurrences(event, now)
        return eventOccurrenceRepository.findFirstByEventIdAndStartTimeGreaterThanEqualAndStatusOrderByStartTimeAsc(
            event.id!!,
            now,
        ) ?: eventOccurrenceRepository.findFirstByEventIdAndStatusOrderByStartTimeAsc(event.id!!)
    }

    @Transactional
    fun ensureUpcomingOccurrences(event: Event, now: LocalDateTime) {
        if (event.recurrenceFrequency == RecurrenceFrequency.NONE) return

        val horizon = now.plusWeeks(DEFAULT_HORIZON_WEEKS)
        var cursor = now
        var generated = 0
        while (generated < MAX_GENERATED_PER_CALL) {
            val nextStart = RecurringEventVisibility.nextOccurrenceStart(event, cursor) ?: break
            if (nextStart.isAfter(horizon)) break

            val nextEnd = RecurringEventVisibility.nextOccurrenceEnd(event, cursor)
                ?: nextStart.plus(
                    Duration.between(event.startTime ?: nextStart, event.endTime ?: nextStart.plusHours(1)),
                )

            if (!eventOccurrenceRepository.existsByEventIdAndStartTime(event.id!!, nextStart)) {
                val occurrence = EventOccurrence().apply {
                    this.event = event
                    this.startTime = nextStart
                    this.endTime = nextEnd
                    this.status = OccurrenceStatus.SCHEDULED
                    this.createdBy = event.createdBy
                }
                event.occurrences.add(occurrence)
                val saved = eventOccurrenceRepository.save(occurrence)
                copyHostsFromFirstOccurrence(event, saved)
                generated++
            }

            cursor = nextStart.plusSeconds(1)
        }
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

    private fun copyHostsFromFirstOccurrence(event: Event, occurrence: EventOccurrence) {
        val firstOccurrence = eventOccurrenceRepository.findFirstByEventIdAndStatusOrderByStartTimeAsc(event.id!!)
            ?: return
        for (existing in firstOccurrence.participants.filter {
            it.role == EventRole.HOST || it.role == EventRole.COHOST
        }) {
            addHostParticipant(occurrence, existing.id.userId)
        }
    }

    companion object {
        private const val DEFAULT_HORIZON_WEEKS = 8L
        private const val MAX_GENERATED_PER_CALL = 52
    }
}
