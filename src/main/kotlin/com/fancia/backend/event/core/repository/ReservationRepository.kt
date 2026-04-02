package com.fancia.backend.event.core.repository

import com.fancia.backend.event.core.entity.Reservation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ReservationRepository : JpaRepository<Reservation, UUID> {
    fun findByIdEventIdAndIdUserId(eventId: UUID, userId: UUID): Reservation?
    fun existsByIdEventIdAndIdUserId(eventId: UUID, userId: UUID): Boolean
}