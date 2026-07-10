package com.fancia.backend.event.core.repository

import com.fancia.backend.event.core.entity.EventOccurrence
import com.fancia.backend.shared.event.core.enums.OccurrenceStatus
import com.fancia.backend.shared.event.core.enums.ReservationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface EventOccurrenceRepository : JpaRepository<EventOccurrence, UUID> {
    fun findByIdAndEventId(id: UUID, eventId: UUID): EventOccurrence?

    fun findByEventIdOrderByStartTimeAsc(eventId: UUID, pageable: Pageable): Page<EventOccurrence>

    fun existsByEventIdAndStartTime(eventId: UUID, startTime: LocalDateTime): Boolean

    fun findFirstByEventIdAndStartTimeGreaterThanEqualAndStatusOrderByStartTimeAsc(
        eventId: UUID,
        from: LocalDateTime,
        status: OccurrenceStatus = OccurrenceStatus.SCHEDULED,
    ): EventOccurrence?

    fun findFirstByEventIdAndStatusOrderByStartTimeAsc(
        eventId: UUID,
        status: OccurrenceStatus = OccurrenceStatus.SCHEDULED,
    ): EventOccurrence?

    @Query(
        """
        SELECT DISTINCT eo
        FROM EventOccurrence eo
        JOIN eo.participants p
        WHERE p.id.userId = :userId
          AND eo.startTime >= :from
          AND eo.status = :status
        """,
    )
    fun findUpcomingForParticipant(
        @Param("userId") userId: UUID,
        @Param("from") from: LocalDateTime,
        @Param("status") status: OccurrenceStatus = OccurrenceStatus.SCHEDULED,
    ): List<EventOccurrence>

    @Query(
        """
        SELECT DISTINCT eo
        FROM EventOccurrence eo
        JOIN eo.reservations r
        WHERE r.id.userId = :userId
          AND eo.startTime >= :from
          AND r.status IN :statuses
          AND eo.status = :occurrenceStatus
        """,
    )
    fun findUpcomingForReservation(
        @Param("userId") userId: UUID,
        @Param("from") from: LocalDateTime,
        @Param("statuses") statuses: Collection<ReservationStatus>,
        @Param("occurrenceStatus") occurrenceStatus: OccurrenceStatus = OccurrenceStatus.SCHEDULED,
    ): List<EventOccurrence>

    @Query(
        """
        SELECT DISTINCT e
        FROM Event e
        WHERE e.createdBy = :userId
           OR EXISTS (
                SELECT 1
                FROM EventOccurrence eo
                JOIN eo.participants p
                WHERE eo.event = e
                  AND p.id.userId = :userId
           )
        ORDER BY e.startTime DESC NULLS LAST
        """,
    )
    fun findEventsByUserInvolvement(
        @Param("userId") userId: UUID,
        pageable: Pageable,
    ): Page<com.fancia.backend.event.core.entity.Event>
}
