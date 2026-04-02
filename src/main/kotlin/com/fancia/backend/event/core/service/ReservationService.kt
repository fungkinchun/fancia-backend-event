package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.dto.CreateReservationRequest
import com.fancia.backend.event.core.dto.ReservationResponse
import com.fancia.backend.event.core.dto.UpdateReservationRequest
import com.fancia.backend.event.core.entity.EventRole
import com.fancia.backend.event.core.entity.ReservationId
import com.fancia.backend.event.core.entity.ReservationStatus
import com.fancia.backend.event.core.exception.*
import com.fancia.backend.event.core.repository.EventParticipantRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.repository.ReservationRepository
import com.fancia.backend.event.mapper.ReservationMapper
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
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
    fun create(request: @Valid CreateReservationRequest, jwt: Jwt): ReservationResponse {
        val userId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val event = eventRepository.findByIdOrNull(request.eventId)
            ?: throw EventNotFoundException(request.eventId)
        if (reservationRepository.existsByIdEventIdAndIdUserId(request.eventId, userId)) {
            throw ReservationAlreadyExistsException(request.eventId, userId)
        }
        val reservation = reservationMapper.toBean(request)
        reservation.event = event
        reservation.id = ReservationId(
            eventId = event.id!!,
            userId
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
        return reservationMapper.toDto(reservationRepository.save(reservation))
    }
}