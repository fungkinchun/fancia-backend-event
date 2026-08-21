package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.EventTicketTier
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.repository.EventTicketTierRepository
import com.fancia.backend.event.external.PaymentInternalClient
import com.fancia.backend.event.mapper.applyTo
import com.fancia.backend.event.mapper.toDto
import com.fancia.backend.event.mapper.toEntity
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.event.core.dto.CreateEventTicketTierRequest
import com.fancia.backend.shared.event.core.dto.EventTicketTierResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventTicketTierRequest
import com.fancia.backend.shared.event.core.exception.EventHostPayoutNotReadyException
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import com.fancia.backend.shared.event.core.exception.EventTicketTierNotFoundException
import com.fancia.backend.shared.event.core.exception.ReservationChangeDeniedException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EventTicketTierService(
    private val eventRepository: EventRepository,
    private val eventTicketTierRepository: EventTicketTierRepository,
    private val paymentInternalClient: PaymentInternalClient,
) {
    @Transactional(readOnly = true)
    fun list(eventId: UUID): List<EventTicketTierResponse> {
        requireEvent(eventId)
        return eventTicketTierRepository.findByEventIdOrderBySortOrderAscCreatedAtAsc(eventId)
            .map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun get(eventId: UUID, tierId: UUID): EventTicketTierResponse =
        requireTier(eventId, tierId).toDto()

    @Transactional
    fun create(eventId: UUID, request: CreateEventTicketTierRequest, jwt: Jwt): EventTicketTierResponse {
        val userId = jwt.userId()
        val event = requireOwnedEvent(eventId, userId)
        if (request.priceMinor > 0) {
            requireHostPayoutReady(eventId, event.createdBy)
        }
        val tier = request.toEntity(event).also { it.createdBy = userId }
        return eventTicketTierRepository.save(tier).toDto()
    }

    @Transactional
    fun update(
        eventId: UUID,
        tierId: UUID,
        request: UpdateEventTicketTierRequest,
        jwt: Jwt,
    ): EventTicketTierResponse {
        val userId = jwt.userId()
        requireOwnedEvent(eventId, userId)
        val tier = requireTier(eventId, tierId)
        request.applyTo(tier)
        if (tier.priceMinor > 0) {
            requireHostPayoutReady(eventId, requireEvent(eventId).createdBy)
        }
        return eventTicketTierRepository.save(tier).toDto()
    }

    @Transactional
    fun delete(eventId: UUID, tierId: UUID, jwt: Jwt) {
        val userId = jwt.userId()
        requireOwnedEvent(eventId, userId)
        val tier = requireTier(eventId, tierId)
        eventTicketTierRepository.delete(tier)
    }

    fun requireTierEntity(eventId: UUID, tierId: UUID): EventTicketTier = requireTier(eventId, tierId)

    private fun requireHostPayoutReady(eventId: UUID, hostUserId: UUID?) {
        val hostId = hostUserId ?: throw EventHostPayoutNotReadyException(eventId = eventId)
        val readiness = paymentInternalClient.payoutReadiness(hostId)
        if (!readiness.payoutsReady) {
            throw EventHostPayoutNotReadyException(eventId = eventId, hostUserId = hostId)
        }
    }

    private fun requireEvent(eventId: UUID) =
        eventRepository.findByIdOrNull(eventId) ?: throw EventNotFoundException(eventId)

    private fun requireOwnedEvent(eventId: UUID, userId: UUID) =
        requireEvent(eventId).also {
            if (it.createdBy != userId) {
                throw ReservationChangeDeniedException(eventId = eventId, userId)
            }
        }

    private fun requireTier(eventId: UUID, tierId: UUID): EventTicketTier =
        eventTicketTierRepository.findByIdAndEventId(tierId, eventId)
            .orElseThrow { EventTicketTierNotFoundException(tierId) }

    private fun Jwt.userId(): UUID =
        getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
