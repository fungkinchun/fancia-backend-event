package com.fancia.backend.event.core.repository

import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.event.core.entity.EventParticipantId
import com.fancia.backend.shared.event.core.enums.EventRole
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EventParticipantRepository : JpaRepository<EventParticipant, EventParticipantId> {
    fun findByIdOccurrenceId(occurrenceId: UUID, pageable: Pageable): Page<EventParticipant>

    fun existsByIdOccurrenceIdAndIdUserIdAndRole(
        occurrenceId: UUID,
        userId: UUID,
        role: EventRole = EventRole.HOST,
    ): Boolean

    fun existsByIdOccurrenceIdAndIdUserId(occurrenceId: UUID, userId: UUID): Boolean
}
