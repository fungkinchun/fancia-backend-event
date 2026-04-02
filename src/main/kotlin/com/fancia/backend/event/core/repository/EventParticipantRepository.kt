package com.fancia.backend.event.core.repository

import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.event.core.entity.EventRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EventParticipantRepository : JpaRepository<EventParticipant, UUID> {
    fun existsByEventIdAndUserIdAndRole(
        eventId: UUID,
        userId: UUID,
        role: EventRole = EventRole.HOST
    ): Boolean
}