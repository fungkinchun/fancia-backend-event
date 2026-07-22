package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.EventOccurrence
import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.event.core.entity.EventParticipantId
import com.fancia.backend.event.core.entity.ReservationId
import com.fancia.backend.event.core.repository.EventParticipantRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.repository.ReservationRepository
import com.fancia.backend.event.mapper.toEntity
import com.fancia.backend.event.mapper.toDto
import com.fancia.backend.shared.event.core.dto.ReservationResponse
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.event.core.dto.CreateReservationRequest
import com.fancia.backend.shared.event.core.dto.UpdateReservationRequest
import com.fancia.backend.shared.event.core.enums.EventRole
import com.fancia.backend.shared.event.core.enums.ReservationStatus
import com.fancia.backend.shared.event.core.exception.*
import jakarta.validation.Valid
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class ReservationService(
    private val eventRepository: EventRepository,
    private val eventOccurrenceService: EventOccurrenceService,
    private val eventParticipantRepository: EventParticipantRepository,
    private val reservationRepository: ReservationRepository,
    private val eventUserTagSyncService: EventUserTagSyncService,
) {
    @Transactional(readOnly = true)
    fun get(
        eventId: UUID,
        occurrenceId: UUID,
        userId: UUID,
        jwt: Jwt,
    ): ReservationResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        eventRepository.findByIdOrNull(eventId) ?: throw EventNotFoundException(eventId)
        eventOccurrenceService.getOccurrence(eventId, occurrenceId)
        val isAdmin = eventParticipantRepository.existsByIdOccurrenceIdAndIdUserIdAndRole(
            occurrenceId,
            currentUserId,
            EventRole.HOST,
        )
        if (!isAdmin && currentUserId != userId) {
            throw ReservationChangeDeniedException(eventId = eventId, userId)
        }
        val reservation = reservationRepository.findByIdOccurrenceIdAndIdUserId(occurrenceId, userId)
            ?: throw ReservationNotFoundException(eventId, userId)
        return reservation.toDto(eventId)
    }

    @Transactional
    fun create(
        eventId: UUID,
        occurrenceId: UUID,
        request: @Valid CreateReservationRequest,
        jwt: Jwt,
    ): ReservationResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val event = eventRepository.findByIdOrNull(eventId)
            ?: throw EventNotFoundException(eventId)
        val occurrence = eventOccurrenceService.getOccurrence(eventId, occurrenceId)
        reservationRepository.findByIdOccurrenceIdAndIdUserId(occurrenceId, currentUserId)?.let {
            return it.toDto(eventId)
        }
        val reservation = request.toEntity()
        reservation.occurrence = occurrence
        reservation.id = ReservationId(
            occurrenceId = occurrence.id!!,
            userId = currentUserId,
        )
        if (!event.approvalRequired) {
            reservation.status = ReservationStatus.ACCEPTED
            addGuestParticipant(occurrence, currentUserId)
        }
        val saved = reservationRepository.save(reservation)
        eventUserTagSyncService.syncEventTagsOnJoin(currentUserId, event)
        return saved.toDto(eventId)
    }

    @Transactional
    fun update(
        eventId: UUID,
        occurrenceId: UUID,
        userId: UUID,
        request: @Valid UpdateReservationRequest,
        jwt: Jwt,
    ): ReservationResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val event = eventRepository.findByIdOrNull(eventId) ?: throw EventNotFoundException(eventId)
        val occurrence = eventOccurrenceService.getOccurrence(eventId, occurrenceId)
        val isAdmin = eventParticipantRepository.existsByIdOccurrenceIdAndIdUserIdAndRole(
            occurrenceId,
            currentUserId,
            EventRole.HOST,
        )
        val reservation = reservationRepository.findByIdOccurrenceIdAndIdUserId(occurrenceId, userId)
            ?: throw ReservationNotFoundException(eventId, userId)

        val isReRequest =
            request.status == ReservationStatus.PENDING &&
                (
                    reservation.status == ReservationStatus.WITHDREW ||
                        reservation.status == ReservationStatus.DENIED
                    )

        when {
            !isAdmin && currentUserId != userId ->
                throw ReservationChangeDeniedException(eventId = eventId, userId)

            !isAdmin &&
                request.status != ReservationStatus.WITHDREW &&
                !isReRequest ->
                throw ReservationStatusChangeAccessDeniedException()
        }

        request.toEntity(reservation)
        if (isReRequest && !event.approvalRequired) {
            reservation.status = ReservationStatus.ACCEPTED
        }

        when (reservation.status) {
            ReservationStatus.ACCEPTED -> addGuestParticipant(occurrence, userId)
            ReservationStatus.WITHDREW -> {
                occurrence.participants.removeIf { it.id.userId == userId }
            }
            else -> {}
        }
        return reservationRepository.save(reservation).toDto(eventId)
    }

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
}
