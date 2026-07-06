package com.fancia.backend.event.core.repository

import com.fancia.backend.event.core.entity.Event
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EventRepository : JpaRepository<Event, UUID> {
    @Query(
        """
    SELECT e
    FROM Event e
    WHERE (
           (:name = '' AND :description = '')
           OR trgm_word_similarity(:name, e.name) = true
           OR trgm_word_similarity(:description, e.description) = true
      )
      AND (:filterByTagIds = false OR EXISTS (SELECT tag FROM e.tags tag WHERE tag IN :tagIds))
    GROUP BY e
""",
    )
    fun search(
        @Param("name") name: String,
        @Param("description") description: String,
        @Param("filterByTagIds") filterByTagIds: Boolean,
        @Param("tagIds") tagIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Event>

    @Query(
        """
    SELECT DISTINCT e
    FROM Event e
    JOIN e.tags tag
    WHERE tag IN :tagIds
""",
    )
    fun findByTagIdIn(
        @Param("tagIds") tagIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Event>

    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): Event?

    @Query(
        """
        SELECT DISTINCT e
        FROM Event e
        LEFT JOIN e.participants p
        WHERE e.createdBy = :userId OR p.id.userId = :userId
        ORDER BY e.startTime DESC NULLS LAST
        """,
    )
    fun findByUserInvolvement(
        @Param("userId") userId: UUID,
        pageable: Pageable,
    ): Page<Event>

    @Query("SELECT e FROM Event e WHERE :tagId MEMBER OF e.tags")
    fun findByTagId(@Param("tagId") tagId: UUID): List<Event>

    @Query(
        """
        SELECT DISTINCT e
        FROM Event e
        JOIN e.participants p
        WHERE p.id.userId = :userId
          AND e.startTime >= :from
        """,
    )
    fun findUpcomingForParticipant(
        @Param("userId") userId: UUID,
        @Param("from") from: java.time.LocalDateTime,
    ): List<Event>

    @Query(
        """
        SELECT DISTINCT e
        FROM Event e
        JOIN e.reservations r
        WHERE r.id.userId = :userId
          AND r.status IN :statuses
          AND e.startTime >= :from
        """,
    )
    fun findUpcomingForReservation(
        @Param("userId") userId: UUID,
        @Param("from") from: java.time.LocalDateTime,
        @Param("statuses") statuses: Collection<com.fancia.backend.shared.event.core.enums.ReservationStatus>,
    ): List<Event>

    @Query(
        value = """
            SELECT e.* FROM events e
            WHERE e.deleted = false
              AND e.latitude IS NOT NULL
              AND e.longitude IS NOT NULL
              AND ST_DWithin(
                geography(ST_SetSRID(ST_MakePoint(e.longitude, e.latitude), 4326)),
                geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)),
                :radiusMeters
              )
            ORDER BY ST_Distance(
                geography(ST_SetSRID(ST_MakePoint(e.longitude, e.latitude), 4326)),
                geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            )
        """,
        countQuery = """
            SELECT count(*) FROM events e
            WHERE e.deleted = false
              AND e.latitude IS NOT NULL
              AND e.longitude IS NOT NULL
              AND ST_DWithin(
                geography(ST_SetSRID(ST_MakePoint(e.longitude, e.latitude), 4326)),
                geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)),
                :radiusMeters
              )
        """,
        nativeQuery = true,
    )
    fun findNearby(
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        @Param("radiusMeters") radiusMeters: Double,
        pageable: Pageable,
    ): Page<Event>
}
