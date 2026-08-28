package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.EventTicketTier
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.repository.EventTicketTierRepository
import com.fancia.backend.event.external.PaymentInternalClient
import com.fancia.backend.event.mapper.applyTo
import com.fancia.backend.event.mapper.toDto
import com.fancia.backend.event.mapper.toEntity
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.core.exception.PremiumFeatureLimitException
import com.fancia.backend.shared.event.core.dto.CreateEventTicketTierRequest
import com.fancia.backend.shared.event.core.dto.EventTicketTierResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventTicketTierRequest
import com.fancia.backend.shared.event.core.entity.Event
import com.fancia.backend.shared.event.core.exception.EventHostPayoutNotReadyException
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import com.fancia.backend.shared.event.core.exception.EventTicketPriceTooSmallException
import com.fancia.backend.shared.event.core.exception.EventTicketTierNotFoundException
import com.fancia.backend.shared.event.core.exception.ReservationChangeDeniedException
import com.fancia.backend.shared.payment.core.util.StripeMinAmounts
import com.fancia.backend.shared.user.core.support.PremiumLimits
import com.fancia.backend.shared.user.core.support.isPremiumClaim
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
        return persistTiers(event, listOf(request), userId, jwt.isPremiumClaim()).single()
    }

    @Transactional
    fun persistTiers(
        event: Event,
        requests: List<CreateEventTicketTierRequest>,
        userId: UUID,
        isPremium: Boolean = false,
    ): List<EventTicketTierResponse> {
        if (requests.isEmpty()) return emptyList()
        val eventId = event.id ?: error("Event must be persisted before ticket tiers")
        requests.forEach {
            requireCataloguePrice(it.priceMinor, it.currency)
            requirePaidTierCapacity(it.priceMinor, it.capacityPerOccurrence, isPremium)
        }
        if (requests.any { it.priceMinor > 0 }) {
            requireHostPayoutReady(eventId, event.createdBy)
        }
        return requests.map { request ->
            val tier = request.toEntity(event).also { it.createdBy = userId }
            eventTicketTierRepository.save(tier).toDto()
        }
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
        requireCataloguePrice(tier.priceMinor, tier.currency)
        requirePaidTierCapacity(tier.priceMinor, tier.capacityPerOccurrence, jwt.isPremiumClaim())
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

    private fun requireCataloguePrice(priceMinor: Long, currency: String) {
        if (StripeMinAmounts.isAllowedCataloguePrice(priceMinor, currency)) return
        throw EventTicketPriceTooSmallException(
            message = "Paid ticket price must be at least ${StripeMinAmounts.formatMinimum(currency)} " +
                "(Stripe card payment minimum). Use 0 for free tickets.",
        )
    }

    private fun requirePaidTierCapacity(priceMinor: Long, capacity: Int?, isPremium: Boolean) {
        if (priceMinor <= 0) return
        val max = PremiumLimits.maxPaidTierCapacity(isPremium)
        if (max == null) return
        if (capacity == null || capacity > max) {
            throw PremiumFeatureLimitException(
                "Free plan allows up to $max seats per paid pricing tier. " +
                    "Upgrade to Fancia Premium for higher paid event capacity.",
            )
        }
    }

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
