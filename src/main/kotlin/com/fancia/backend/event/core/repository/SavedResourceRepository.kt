package com.fancia.backend.event.core.repository

import com.fancia.backend.shared.common.saved.core.entity.SavedResource
import com.fancia.backend.shared.common.saved.core.entity.SavedResourceId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SavedResourceRepository : JpaRepository<SavedResource, SavedResourceId> {
    fun findByIdUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): Page<SavedResource>

    fun existsByIdUserIdAndIdResourceId(userId: UUID, resourceId: UUID): Boolean

    fun deleteByIdUserIdAndIdResourceId(userId: UUID, resourceId: UUID)
}
