package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.event.core.entity.ReservationId
import com.fancia.backend.event.core.repository.EventParticipantRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.repository.ReservationRepository
import com.fancia.backend.event.mapper.ReservationMapper
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.event.core.dto.CreateReservationRequest
import com.fancia.backend.shared.event.core.dto.ReservationResponse
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
    private val eventParticipantRepository: EventParticipantRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationMapper: ReservationMapper,
) {
    @Transactional
    fun create(eventId: UUID, request: @Valid CreateReservationRequest, jwt: Jwt): ReservationResponse {
        val requesterId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val event = eventRepository.findByIdOrNull(eventId)
            ?: throw EventNotFoundException(eventId)
        if (reservationRepository.existsByIdEventIdAndIdUserId(eventId, requesterId)) {
            throw ReservationAlreadyExistsException(eventId, requesterId)
        }
        val reservation = reservationMapper.toBean(request)
        reservation.event = event
        reservation.id = ReservationId(
            eventId = event.id!!,
            requesterId
        )
        return reservationRepository.save(reservation).let(reservationMapper::toDto)
    }

    @Transactional
    fun update(eventId: UUID, userId: UUID, request: @Valid UpdateReservationRequest, jwt: Jwt): ReservationResponse {
        val requesterId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val isAdmin = eventParticipantRepository.existsByEventIdAndUserIdAndRole(
            eventId,
            requesterId,
            EventRole.HOST
        )

        when {
            !isAdmin && requesterId != userId ->
                throw ReservationChangeDeniedException(eventId = eventId, userId)

            !isAdmin && request.status != ReservationStatus.WITHDREW ->
                throw ReservationStatusChangeAccessDeniedException()
        }
        val reservation = reservationRepository.findByIdEventIdAndIdUserId(eventId, userId)
            ?: throw ReservationNotFoundException(eventId, userId)
        reservationMapper.toBean(request, reservation)
        val event = eventRepository.findByIdOrNull(eventId)
            ?: throw EventNotFoundException(eventId)

        when (request.status) {
            ReservationStatus.ACCEPTED -> {
                if (event.participants.none { it.userId == userId }) {
                    val newParticipant = EventParticipant(userId = userId).apply {
                        this.event = event
                    }
                    event.participants.add(newParticipant)
                    eventRepository.save(event)
                }
            }

            ReservationStatus.WITHDREW -> {
                event.participants.removeIf { it.userId == userId }
                eventRepository.save(event)
            }

            else -> {}
        }
        return reservationMapper.toDto(reservationRepository.save(reservation))
    }
}