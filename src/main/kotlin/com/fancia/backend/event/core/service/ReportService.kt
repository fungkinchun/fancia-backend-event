package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.ReportRepository
import com.fancia.backend.event.external.UserServiceClient
import com.fancia.backend.shared.common.core.exception.DomainException
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.moderation.core.dto.CreateBlockedResourceRequest
import com.fancia.backend.shared.common.moderation.core.dto.CreateReportRequest
import com.fancia.backend.shared.common.moderation.core.dto.ReportResponse
import com.fancia.backend.shared.common.moderation.core.entity.Report
import com.fancia.backend.shared.common.moderation.core.enums.BlockedResourceType
import com.fancia.backend.shared.common.moderation.core.enums.ReportStatus
import com.fancia.backend.shared.common.moderation.core.exception.UnsupportedBlockedResourceTypeException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val blockedResourceService: BlockedResourceService,
    private val userServiceClient: UserServiceClient,
) {
    @Transactional
    fun create(request: CreateReportRequest, jwt: Jwt): ReportResponse {
        val userId = currentUserId(jwt)
        if (request.targetType != BlockedResourceType.EVENT) {
            throw UnsupportedBlockedResourceTypeException()
        }

        val report = Report().apply {
            reporterUserId = userId
            createdBy = userId
            targetType = request.targetType
            targetId = request.targetId
            reason = request.reason
            details = request.details?.trim()?.ifBlank { null }
            status = ReportStatus.OPEN
        }
        val saved = reportRepository.save(report)

        if (request.alsoHideResource) {
            blockedResourceService.block(
                CreateBlockedResourceRequest(
                    resourceType = BlockedResourceType.EVENT,
                    resourceId = request.targetId,
                ),
                jwt,
            )
        }

        if (request.alsoBlockUser) {
            val ownerId = request.targetOwnerUserId
                ?: throw DomainException(
                    title = "Owner Required",
                    message = "targetOwnerUserId is required when alsoBlockUser is true",
                    errorCode = "TARGET_OWNER_REQUIRED",
                )
            userServiceClient.block(
                CreateBlockedResourceRequest(
                    resourceType = BlockedResourceType.USER,
                    resourceId = ownerId,
                ),
            )
        }

        return saved.toResponse()
    }

    private fun currentUserId(jwt: Jwt): UUID =
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()

    private fun Report.toResponse() = ReportResponse(
        id = id,
        targetType = targetType,
        targetId = targetId!!,
        reason = reason,
        details = details,
        status = status,
        createdAt = createdAt,
    )
}
