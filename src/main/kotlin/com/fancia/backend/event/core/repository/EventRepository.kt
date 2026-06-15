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
    WHERE (:interestGroupId IS NULL OR :interestGroupId MEMBER OF e.interestGroups)
      AND (
           e.visibility = com.fancia.backend.shared.event.core.enums.EventVisibility.PUBLIC
           OR (
               e.visibility = com.fancia.backend.shared.event.core.enums.EventVisibility.GROUP
               AND :interestGroupId IS NOT NULL
               AND :interestGroupId MEMBER OF e.interestGroups
           )
      )
      AND (
           (:name = '' AND :description = '' AND :tags = '')
           OR trgm_word_similarity(:name, e.name) = true
           OR trgm_word_similarity(:description, e.description) = true
           OR trgm_word_similarity(:tags,
              (SELECT LISTAGG(t, ',') WITHIN GROUP (ORDER BY t) FROM e.tags t)
           ) = true
      )
    GROUP BY e
"""
    )
    fun findAll(
        @Param("name") name: String,
        @Param("description") description: String,
        @Param("tags") tags: String,
        @Param("interestGroupId") interestGroupId: UUID?,
        pageable: Pageable
    ): Page<Event>

    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): Event?

    @Query(
        value = """
            SELECT e.* FROM events e
            WHERE e.deleted = false
              AND e.latitude IS NOT NULL
              AND e.longitude IS NOT NULL
              AND (
                (:interestGroupId IS NULL AND e.visibility = 'PUBLIC')
                OR (
                  :interestGroupId IS NOT NULL
                  AND (
                    e.visibility = 'PUBLIC'
                    OR (
                      e.visibility = 'GROUP'
                      AND EXISTS (
                        SELECT 1 FROM event_interest_groups eig
                        WHERE eig.event_id = e.id
                          AND eig.event_interest_groups = :interestGroupId
                      )
                    )
                  )
                )
              )
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
              AND (
                (:interestGroupId IS NULL AND e.visibility = 'PUBLIC')
                OR (
                  :interestGroupId IS NOT NULL
                  AND (
                    e.visibility = 'PUBLIC'
                    OR (
                      e.visibility = 'GROUP'
                      AND EXISTS (
                        SELECT 1 FROM event_interest_groups eig
                        WHERE eig.event_id = e.id
                          AND eig.event_interest_groups = :interestGroupId
                      )
                    )
                  )
                )
              )
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
        @Param("interestGroupId") interestGroupId: UUID?,
        pageable: Pageable,
    ): Page<Event>
}