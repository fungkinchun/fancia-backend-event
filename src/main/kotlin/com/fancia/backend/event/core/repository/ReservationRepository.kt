package com.fancia.backend.event.core.repository

import com.fancia.backend.shared.event.core.entity.Reservation
import com.fancia.backend.shared.event.core.entity.ReservationId
import com.fancia.backend.shared.event.core.enums.ReservationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ReservationRepository : JpaRepository<Reservation, ReservationId> {
    fun findByIdOccurrenceIdAndIdUserId(occurrenceId: UUID, userId: UUID): Reservation?

    fun existsByIdOccurrenceIdAndIdUserId(occurrenceId: UUID, userId: UUID): Boolean

    fun findByIdOccurrenceId(occurrenceId: UUID, pageable: Pageable): Page<Reservation>

    fun findByIdOccurrenceIdAndStatus(
        occurrenceId: UUID,
        status: ReservationStatus,
        pageable: Pageable,
    ): Page<Reservation>

    @Query(
        """
        select count(r) from Reservation r
        where r.id.occurrenceId = :occurrenceId
          and r.tierId = :tierId
          and r.status in (
            com.fancia.backend.shared.event.core.enums.ReservationStatus.PAID,
            com.fancia.backend.shared.event.core.enums.ReservationStatus.ACCEPTED,
            com.fancia.backend.shared.event.core.enums.ReservationStatus.WHITELIST
          )
        """,
    )
    fun countClaimedSeats(
        @Param("occurrenceId") occurrenceId: UUID,
        @Param("tierId") tierId: UUID,
    ): Long
}
