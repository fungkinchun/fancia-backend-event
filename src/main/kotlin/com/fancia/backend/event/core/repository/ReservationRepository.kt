package com.fancia.backend.event.core.repository

import com.fancia.backend.event.core.entity.Reservation
import com.fancia.backend.event.core.entity.ReservationId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ReservationRepository : JpaRepository<Reservation, ReservationId> {
    fun findByIdOccurrenceIdAndIdUserId(occurrenceId: UUID, userId: UUID): Reservation?

    fun existsByIdOccurrenceIdAndIdUserId(occurrenceId: UUID, userId: UUID): Boolean
}
