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
    WHERE trgm_word_similarity(:name, e.name) = true
       OR trgm_word_similarity(:description, e.description) = true
       OR trgm_word_similarity(:tags, 
       (SELECT LISTAGG(t, ',') WITHIN GROUP (ORDER BY t) FROM e.tags t)
       ) = true
    GROUP BY e
"""
    )
    fun findAll(
        @Param("name") name: String,
        @Param("description") description: String,
        @Param("tags") tags: String,
        pageable: Pageable
    ): Page<Event>

    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): Event?
}