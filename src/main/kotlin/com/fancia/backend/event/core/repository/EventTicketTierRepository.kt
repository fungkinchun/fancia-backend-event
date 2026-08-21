package com.fancia.backend.event.core.repository

import com.fancia.backend.event.core.entity.EventTicketTier
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface EventTicketTierRepository : JpaRepository<EventTicketTier, UUID> {
    fun findByEventIdOrderBySortOrderAscCreatedAtAsc(eventId: UUID): List<EventTicketTier>

    fun findByIdAndEventId(id: UUID, eventId: UUID): Optional<EventTicketTier>
}
