package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.EventParticipantRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.repository.EventTicketTierRepository
import com.fancia.backend.event.core.repository.ReservationRepository
import com.fancia.backend.event.core.support.CheckInTokens
import com.fancia.backend.event.external.PaymentInternalClient
import com.fancia.backend.event.mapper.toDto
import com.fancia.backend.event.mapper.toEntity
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.event.core.dto.CreateReservationRequest
import com.fancia.backend.shared.event.core.dto.EventReservationCheckoutSnapshot
import com.fancia.backend.shared.event.core.dto.ReservationResponse
import com.fancia.backend.shared.event.core.dto.UpdateReservationRequest
import com.fancia.backend.shared.event.core.entity.Event
import com.fancia.backend.shared.event.core.entity.EventOccurrence
import com.fancia.backend.shared.event.core.entity.EventParticipant
import com.fancia.backend.shared.event.core.entity.EventParticipantId
import com.fancia.backend.shared.event.core.entity.Reservation
import com.fancia.backend.shared.event.core.entity.ReservationId
import com.fancia.backend.shared.event.core.enums.EventRole
import com.fancia.backend.shared.event.core.enums.ReservationStatus
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import com.fancia.backend.shared.event.core.exception.EventTicketSoldOutException
import com.fancia.backend.shared.event.core.exception.EventTicketTierNotFoundException
import com.fancia.backend.shared.event.core.exception.ReservationChangeDeniedException
import com.fancia.backend.shared.event.core.exception.ReservationEarlyAccessException
import com.fancia.backend.shared.event.core.exception.ReservationNotFoundException
import com.fancia.backend.shared.event.core.exception.ReservationRefundFailedException
import com.fancia.backend.shared.event.core.exception.ReservationStatusChangeAccessDeniedException
import com.fancia.backend.shared.payment.core.dto.ConnectCheckoutRequest
import com.fancia.backend.shared.payment.core.dto.ConnectCheckoutResponse
import com.fancia.backend.shared.payment.core.dto.CreateConnectCheckoutSessionRequest
import com.fancia.backend.shared.payment.core.dto.RefundConnectCheckoutRequest
import com.fancia.backend.shared.payment.core.enums.ConnectCheckoutPurpose
import com.fancia.backend.shared.user.core.support.PremiumLimits
import com.fancia.backend.shared.user.core.support.isPremiumClaim
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class ReservationService(
    private val eventRepository: EventRepository,
    private val eventOccurrenceService: EventOccurrenceService,
    private val eventParticipantRepository: EventParticipantRepository,
    private val reservationRepository: ReservationRepository,
    private val eventTicketTierRepository: EventTicketTierRepository,
    private val eventUserTagSyncService: EventUserTagSyncService,
    private val paymentInternalClient: PaymentInternalClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val postPaidHostStatuses = setOf(
        ReservationStatus.ACCEPTED,
        ReservationStatus.DENIED,
        ReservationStatus.WHITELIST,
    )

    private val withdrawableStatuses = setOf(
        ReservationStatus.PENDING,
    )

    private val refundableStatuses = setOf(
        ReservationStatus.PAID,
        ReservationStatus.ACCEPTED,
        ReservationStatus.WHITELIST,
    )

    @Transactional
    fun get(
        eventId: UUID,
        occurrenceId: UUID,
        userId: UUID,
        jwt: Jwt,
    ): ReservationResponse {
        val currentUserId = jwt.userId()
        eventRepository.findByIdOrNull(eventId) ?: throw EventNotFoundException(eventId)
        eventOccurrenceService.getOccurrence(eventId, occurrenceId)
        val isAdmin = isHost(occurrenceId, currentUserId)
        if (!isAdmin && currentUserId != userId) {
            throw ReservationChangeDeniedException(eventId = eventId, userId)
        }
        val reservation = reservationRepository.findByIdOccurrenceIdAndIdUserId(occurrenceId, userId)
            ?: throw ReservationNotFoundException(eventId, userId)
        if (ensureCheckInToken(reservation)) {
            reservationRepository.save(reservation)
        }
        val includeToken = currentUserId == userId && reservation.status == ReservationStatus.ACCEPTED
        return reservation.toDto(eventId, includeCheckInToken = includeToken)
    }

    @Transactional(readOnly = true)
    fun list(
        eventId: UUID,
        occurrenceId: UUID,
        status: ReservationStatus?,
        pageable: Pageable,
        jwt: Jwt,
    ): Page<ReservationResponse> {
        val currentUserId = jwt.userId()
        eventRepository.findByIdOrNull(eventId) ?: throw EventNotFoundException(eventId)
        eventOccurrenceService.getOccurrence(eventId, occurrenceId)
        if (!isHost(occurrenceId, currentUserId)) {
            throw ReservationChangeDeniedException(eventId = eventId, currentUserId)
        }
        val page = if (status != null) {
            reservationRepository.findByIdOccurrenceIdAndStatus(occurrenceId, status, pageable)
        } else {
            reservationRepository.findByIdOccurrenceId(occurrenceId, pageable)
        }
        return page.map { it.toDto(eventId, includeCheckInToken = false) }
    }

    @Transactional
    fun create(
        eventId: UUID,
        occurrenceId: UUID,
        request: @Valid CreateReservationRequest,
        jwt: Jwt,
    ): ReservationResponse {
        val currentUserId = jwt.userId()
        val event = eventRepository.findByIdOrNull(eventId)
            ?: throw EventNotFoundException(eventId)
        val occurrence = eventOccurrenceService.getOccurrence(eventId, occurrenceId)
        reservationRepository.findByIdOccurrenceIdAndIdUserId(occurrenceId, currentUserId)?.let {
            ensureCheckInToken(it)
            val includeToken = it.status == ReservationStatus.ACCEPTED
            return reservationRepository.save(it).toDto(eventId, includeCheckInToken = includeToken)
        }

        val reservation = request.toEntity()
        reservation.occurrence = occurrence
        reservation.id = ReservationId(
            occurrenceId = occurrence.id!!,
            userId = currentUserId,
        )
        reservation.status = ReservationStatus.PENDING

        val tierId = request.tierId
        if (tierId != null) {
            val tier = eventTicketTierRepository.findByIdAndEventId(tierId, eventId)
                .orElseThrow { EventTicketTierNotFoundException(tierId) }
            assertPaidEarlyAccess(event, tier.priceMinor, jwt)
            reservation.tierId = tier.id
            reservation.priceMinor = tier.priceMinor
            reservation.currency = tier.currency
            assertCapacityAvailable(occurrenceId, tier.id!!, tier.capacityPerOccurrence)
        } else {
            reservation.priceMinor = 0
            reservation.currency = "gbp"
        }

        if ((reservation.priceMinor ?: 0L) == 0L) {
            markPaid(reservation, occurrence, event, currentUserId, checkoutSessionId = null)
        }

        val saved = reservationRepository.save(reservation)
        val includeToken = saved.status == ReservationStatus.ACCEPTED
        return saved.toDto(eventId, includeCheckInToken = includeToken)
    }

    @Transactional
    fun update(
        eventId: UUID,
        occurrenceId: UUID,
        userId: UUID,
        request: @Valid UpdateReservationRequest,
        jwt: Jwt,
    ): ReservationResponse {
        val currentUserId = jwt.userId()
        eventRepository.findByIdOrNull(eventId) ?: throw EventNotFoundException(eventId)
        val occurrence = eventOccurrenceService.getOccurrence(eventId, occurrenceId)
        val isAdmin = isHost(occurrenceId, currentUserId)
        val reservation = reservationRepository.findByIdOccurrenceIdAndIdUserId(occurrenceId, userId)
            ?: throw ReservationNotFoundException(eventId, userId)

        if (!isAdmin && currentUserId != userId) {
            throw ReservationChangeDeniedException(eventId = eventId, userId)
        }

        val previousStatus = reservation.status
        assertStatusTransitionAllowed(isAdmin, previousStatus, request.status)

        if (request.status == ReservationStatus.DENIED) {
            requireRefundIfNeeded(reservation, previousStatus)
        }

        reservation.guests = request.guests
        reservation.payload = request.payload
        reservation.status = request.status

        when (reservation.status) {
            ReservationStatus.DENIED, ReservationStatus.WITHDREW -> {
                occurrence.participants.removeIf { it.id.userId == userId }
            }
            ReservationStatus.ACCEPTED, ReservationStatus.WHITELIST -> {
                if (previousStatus == ReservationStatus.PAID ||
                    previousStatus in postPaidHostStatuses
                ) {
                    addGuestParticipant(occurrence, userId)
                }
            }
            ReservationStatus.PENDING -> {
                occurrence.participants.removeIf { it.id.userId == userId }
            }
            else -> {}
        }

        syncCheckInToken(reservation)
        val saved = reservationRepository.saveAndFlush(reservation)
        val includeToken = currentUserId == userId && saved.status == ReservationStatus.ACCEPTED
        return saved.toDto(eventId, includeCheckInToken = includeToken)
    }

    @Transactional(readOnly = true)
    fun checkoutSnapshot(
        eventId: UUID,
        occurrenceId: UUID,
        userId: UUID,
    ): EventReservationCheckoutSnapshot {
        val event = eventRepository.findByIdOrNull(eventId) ?: throw EventNotFoundException(eventId)
        eventOccurrenceService.getOccurrence(eventId, occurrenceId)
        val reservation = reservationRepository.findByIdOccurrenceIdAndIdUserId(occurrenceId, userId)
            ?: throw ReservationNotFoundException(eventId, userId)
        val tierId = reservation.tierId
            ?: throw EventTicketTierNotFoundException(message = "Reservation has no ticket tier")
        val tier = eventTicketTierRepository.findByIdAndEventId(tierId, eventId)
            .orElseThrow { EventTicketTierNotFoundException(tierId) }
        val hostUserId = event.createdBy
            ?: throw ReservationChangeDeniedException(eventId = eventId, userId)

        assertCapacityAvailable(occurrenceId, tierId, tier.capacityPerOccurrence)

        return EventReservationCheckoutSnapshot(
            eventId = eventId,
            occurrenceId = occurrenceId,
            userId = userId,
            hostUserId = hostUserId,
            tierId = tierId,
            tierName = tier.name,
            priceMinor = reservation.priceMinor ?: tier.priceMinor,
            currency = reservation.currency ?: tier.currency,
            reservationStatus = reservation.status,
        )
    }

    @Transactional(readOnly = true)
    fun checkout(
        eventId: UUID,
        occurrenceId: UUID,
        request: ConnectCheckoutRequest,
        jwt: Jwt,
    ): ConnectCheckoutResponse {
        val userId = jwt.userId()
        val snapshot = checkoutSnapshot(eventId, occurrenceId, userId)
        if (snapshot.reservationStatus != ReservationStatus.PENDING) {
            throw ReservationStatusChangeAccessDeniedException()
        }
        if (snapshot.priceMinor <= 0L) {
            throw ReservationStatusChangeAccessDeniedException()
        }
        return paymentInternalClient.createCheckoutSession(
            CreateConnectCheckoutSessionRequest(
                successUrl = request.successUrl,
                cancelUrl = request.cancelUrl,
                buyerUserId = userId,
                sellerUserId = snapshot.hostUserId,
                amountMinor = snapshot.priceMinor,
                currency = snapshot.currency,
                productName = snapshot.tierName,
                purpose = ConnectCheckoutPurpose.EVENT_TICKET.name,
                resourceId = resourceId(eventId, occurrenceId, userId),
                metadata = mapOf(
                    "eventId" to eventId.toString(),
                    "occurrenceId" to occurrenceId.toString(),
                    "tierId" to snapshot.tierId.toString(),
                ),
            ),
        )
    }

    @Transactional
    fun confirmPaid(
        eventId: UUID,
        occurrenceId: UUID,
        userId: UUID,
        checkoutSessionId: String?,
    ): ReservationResponse {
        val event = eventRepository.findByIdOrNull(eventId) ?: throw EventNotFoundException(eventId)
        val occurrence = eventOccurrenceService.getOccurrence(eventId, occurrenceId)
        val reservation = reservationRepository.findByIdOccurrenceIdAndIdUserId(occurrenceId, userId)
            ?: throw ReservationNotFoundException(eventId, userId)

        if (reservation.status in setOf(
                ReservationStatus.PAID,
                ReservationStatus.ACCEPTED,
                ReservationStatus.WHITELIST,
            )
        ) {
            ensureCheckInToken(reservation)
            return reservationRepository.save(reservation).toDto(eventId, includeCheckInToken = false)
        }
        if (reservation.status != ReservationStatus.PENDING) {
            throw ReservationStatusChangeAccessDeniedException()
        }

        val tierId = reservation.tierId
            ?: throw EventTicketTierNotFoundException(message = "Reservation has no ticket tier")
        val tier = eventTicketTierRepository.findByIdAndEventId(tierId, eventId)
            .orElseThrow { EventTicketTierNotFoundException(tierId) }
        assertCapacityAvailable(occurrenceId, tierId, tier.capacityPerOccurrence)

        markPaid(reservation, occurrence, event, userId, checkoutSessionId)
        return reservationRepository.save(reservation).toDto(eventId, includeCheckInToken = false)
    }

    private fun markPaid(
        reservation: Reservation,
        occurrence: EventOccurrence,
        event: Event,
        userId: UUID,
        checkoutSessionId: String?,
    ) {
        reservation.status = ReservationStatus.PAID
        reservation.stripeCheckoutSessionId = checkoutSessionId ?: reservation.stripeCheckoutSessionId
        reservation.paidAt = LocalDateTime.now(ZoneOffset.UTC)
        addGuestParticipant(occurrence, userId)
        eventUserTagSyncService.syncEventTagsOnJoin(userId, event)
        if (!event.approvalRequired) {
            reservation.status = ReservationStatus.ACCEPTED
        }
        syncCheckInToken(reservation)
    }

    private fun syncCheckInToken(reservation: Reservation) {
        if (reservation.status == ReservationStatus.ACCEPTED) {
            if (reservation.checkInToken.isNullOrBlank()) {
                reservation.checkInToken = CheckInTokens.generate()
            }
        } else {
            reservation.checkInToken = null
        }
    }

    private fun ensureCheckInToken(reservation: Reservation): Boolean {
        if (reservation.status != ReservationStatus.ACCEPTED) return false
        if (!reservation.checkInToken.isNullOrBlank()) return false
        reservation.checkInToken = CheckInTokens.generate()
        return true
    }

    private fun requireRefundIfNeeded(reservation: Reservation, previousStatus: ReservationStatus?) {
        if (previousStatus !in refundableStatuses) return
        if ((reservation.priceMinor ?: 0L) <= 0L) return
        val sessionId = reservation.stripeCheckoutSessionId
            ?: throw ReservationRefundFailedException(
                message = "Cannot change status: paid reservation is missing checkout session for refund",
            )
        try {
            paymentInternalClient.refundCheckout(RefundConnectCheckoutRequest(sessionId))
        } catch (ex: Exception) {
            log.error(
                "Refund failed reservation occurrence={} user={} session={}",
                reservation.id?.occurrenceId,
                reservation.id?.userId,
                sessionId,
                ex,
            )
            throw ReservationRefundFailedException()
        }
    }

    private fun assertCapacityAvailable(
        occurrenceId: UUID,
        tierId: UUID,
        capacity: Int?,
    ) {
        if (capacity == null) return
        val claimed = reservationRepository.countClaimedSeats(occurrenceId, tierId)
        if (claimed >= capacity) {
            throw EventTicketSoldOutException(tierId)
        }
    }

    private fun assertPaidEarlyAccess(event: Event, priceMinor: Long, jwt: Jwt) {
        if (priceMinor <= 0L || jwt.isPremiumClaim()) return
        val createdAt = event.createdAt ?: return
        val opensForFree = createdAt.plusHours(PremiumLimits.PAID_RESERVATION_EARLY_ACCESS_HOURS)
        if (LocalDateTime.now().isBefore(opensForFree)) {
            throw ReservationEarlyAccessException(eventId = event.id!!)
        }
    }

    private fun assertStatusTransitionAllowed(
        isAdmin: Boolean,
        currentStatus: ReservationStatus?,
        requestedStatus: ReservationStatus,
    ) {
        if (isAdmin) {
            val allowed = when (requestedStatus) {
                ReservationStatus.ACCEPTED, ReservationStatus.DENIED, ReservationStatus.WHITELIST ->
                    currentStatus == ReservationStatus.PAID || currentStatus in postPaidHostStatuses
                ReservationStatus.PENDING ->
                    currentStatus == ReservationStatus.DENIED || currentStatus == ReservationStatus.WITHDREW
                else -> false
            }
            if (!allowed) throw ReservationStatusChangeAccessDeniedException()
            return
        }

        val allowed = when (requestedStatus) {
            ReservationStatus.WITHDREW -> currentStatus in withdrawableStatuses
            ReservationStatus.PENDING ->
                currentStatus == ReservationStatus.WITHDREW ||
                    currentStatus == ReservationStatus.DENIED
            else -> false
        }
        if (!allowed) {
            throw ReservationStatusChangeAccessDeniedException()
        }
    }

    private fun isHost(occurrenceId: UUID, userId: UUID): Boolean =
        eventParticipantRepository.existsByIdOccurrenceIdAndIdUserIdAndRole(
            occurrenceId,
            userId,
            EventRole.HOST,
        )

    private fun addGuestParticipant(occurrence: EventOccurrence, userId: UUID) {
        if (occurrence.participants.any { it.id.userId == userId }) return
        val participant = EventParticipant(
            EventParticipantId(
                occurrenceId = occurrence.id!!,
                userId = userId,
            ),
        ).apply {
            this.occurrence = occurrence
            this.role = EventRole.GUEST
        }
        occurrence.participants.add(participant)
    }

    private fun Jwt.userId(): UUID =
        getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()

    companion object {
        fun resourceId(eventId: UUID, occurrenceId: UUID, userId: UUID): String =
            "$eventId:$occurrenceId:$userId"

        fun parseResourceId(resourceId: String): Triple<UUID, UUID, UUID>? {
            val parts = resourceId.split(':')
            if (parts.size != 3) return null
            return runCatching {
                Triple(UUID.fromString(parts[0]), UUID.fromString(parts[1]), UUID.fromString(parts[2]))
            }.getOrNull()
        }
    }
}
