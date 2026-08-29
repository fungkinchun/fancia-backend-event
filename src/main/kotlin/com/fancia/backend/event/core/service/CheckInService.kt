package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.EventTicketTier
import com.fancia.backend.event.core.repository.EventParticipantRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.repository.EventTicketTierRepository
import com.fancia.backend.event.core.repository.ReservationRepository
import com.fancia.backend.event.core.support.CheckInTokens
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.event.core.dto.CheckInRequest
import com.fancia.backend.shared.event.core.dto.CheckInResultResponse
import com.fancia.backend.shared.event.core.dto.CheckInRosterEntry
import com.fancia.backend.shared.event.core.dto.CheckInRosterResponse
import com.fancia.backend.shared.event.core.dto.CheckInSyncRequest
import com.fancia.backend.shared.event.core.dto.CheckInSyncResponse
import com.fancia.backend.shared.event.core.dto.ManualCheckInRequest
import com.fancia.backend.shared.event.core.entity.EventOccurrence
import com.fancia.backend.shared.event.core.entity.Reservation
import com.fancia.backend.shared.event.core.enums.EventRole
import com.fancia.backend.shared.event.core.enums.ReservationStatus
import com.fancia.backend.shared.event.core.exception.CheckInAccessDeniedException
import com.fancia.backend.shared.event.core.exception.CheckInOutsideWindowException
import com.fancia.backend.shared.event.core.exception.CheckInTokenInvalidException
import com.fancia.backend.shared.event.core.exception.EventNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class CheckInService(
    private val eventRepository: EventRepository,
    private val eventOccurrenceService: EventOccurrenceService,
    private val eventParticipantRepository: EventParticipantRepository,
    private val reservationRepository: ReservationRepository,
    private val eventTicketTierRepository: EventTicketTierRepository,
) {
    private val scannerRoles = listOf(EventRole.HOST, EventRole.COHOST)

    @Transactional
    fun checkIn(
        eventId: UUID,
        occurrenceId: UUID,
        request: CheckInRequest,
        jwt: Jwt,
    ): CheckInResultResponse {
        val scannerId = jwt.userId()
        val occurrence = requireOccurrenceForScanner(eventId, occurrenceId, scannerId)
        return performCheckIn(occurrence, request.token.trim(), scannerId, softFail = false)
    }

    @Transactional
    fun manualCheckIn(
        eventId: UUID,
        occurrenceId: UUID,
        request: ManualCheckInRequest,
        jwt: Jwt,
    ): CheckInResultResponse {
        val scannerId = jwt.userId()
        val occurrence = requireOccurrenceForScanner(eventId, occurrenceId, scannerId)
        val reservation = reservationRepository.findByIdOccurrenceIdAndIdUserId(occurrenceId, request.userId)
            ?: throw CheckInTokenInvalidException()
        if (reservation.status != ReservationStatus.ACCEPTED) {
            throw CheckInTokenInvalidException()
        }
        if (ensureToken(reservation)) {
            reservationRepository.save(reservation)
        }
        val token = reservation.checkInToken
            ?: throw CheckInTokenInvalidException()
        return performCheckIn(occurrence, token, scannerId, softFail = false)
    }

    @Transactional
    fun sync(
        eventId: UUID,
        occurrenceId: UUID,
        request: CheckInSyncRequest,
        jwt: Jwt,
    ): CheckInSyncResponse {
        val scannerId = jwt.userId()
        val occurrence = requireOccurrenceForScanner(eventId, occurrenceId, scannerId)
        val results = request.tokens.map { token ->
            performCheckIn(occurrence, token.trim(), scannerId, softFail = true)
        }
        return CheckInSyncResponse(results = results)
    }

    @Transactional
    fun roster(
        eventId: UUID,
        occurrenceId: UUID,
        jwt: Jwt,
    ): CheckInRosterResponse {
        val scannerId = jwt.userId()
        val occurrence = requireOccurrenceForScanner(eventId, occurrenceId, scannerId)
        val accepted = reservationRepository.findAllByIdOccurrenceIdAndStatus(
            occurrenceId,
            ReservationStatus.ACCEPTED,
        )
        val entries = accepted.mapNotNull { reservation ->
            if (ensureToken(reservation)) {
                reservationRepository.save(reservation)
            }
            val token = reservation.checkInToken ?: return@mapNotNull null
            val userId = reservation.id!!.userId!!
            CheckInRosterEntry(
                tokenHash = CheckInTokens.hash(token),
                userId = userId,
                tierName = resolveTierName(eventId, reservation.tierId),
                guestCount = reservation.guests,
                checkedInAt = reservation.checkedInAt,
            )
        }
        return CheckInRosterResponse(
            occurrenceId = occurrenceId,
            startTime = occurrence.startTime,
            endTime = occurrence.endTime,
            entries = entries,
        )
    }

    private fun performCheckIn(
        occurrence: EventOccurrence,
        token: String,
        scannerId: UUID,
        softFail: Boolean,
    ): CheckInResultResponse {
        if (token.isBlank()) {
            return failOrThrow(softFail, "CHECK_IN_TOKEN_INVALID", "Check-in token is required") {
                CheckInTokenInvalidException()
            }
        }

        val reservation = reservationRepository.findByCheckInToken(token)
            ?: return failOrThrow(softFail, "CHECK_IN_TOKEN_INVALID", "No accepted reservation matches this check-in token") {
                CheckInTokenInvalidException()
            }

        if (reservation.id?.occurrenceId != occurrence.id) {
            return failOrThrow(softFail, "CHECK_IN_TOKEN_INVALID", "Token does not belong to this occurrence") {
                CheckInTokenInvalidException()
            }
        }

        if (reservation.status != ReservationStatus.ACCEPTED) {
            return failOrThrow(softFail, "CHECK_IN_TOKEN_INVALID", "Reservation is not accepted") {
                CheckInTokenInvalidException()
            }
        }

        val eventId = occurrence.event!!.id!!
        val (before, after) = resolveBuffers(eventId, reservation.tierId)
        if (!isWithinWindow(occurrence, before, after)) {
            return failOrThrow(
                softFail,
                "CHECK_IN_OUTSIDE_WINDOW",
                "Check-in is only allowed within this ticket's occurrence window",
            ) {
                CheckInOutsideWindowException()
            }
        }

        val userId = reservation.id!!.userId!!
        val tierName = resolveTierName(eventId, reservation.tierId)
        val guestCount = reservation.guests

        if (reservation.checkedInAt != null) {
            return CheckInResultResponse(
                tokenAccepted = true,
                alreadyCheckedIn = true,
                checkedInAt = reservation.checkedInAt,
                userId = userId,
                tierName = tierName,
                guestCount = guestCount,
            )
        }

        val now = LocalDateTime.now(ZoneOffset.UTC)
        reservation.checkedInAt = now
        reservation.checkedInBy = scannerId
        reservationRepository.save(reservation)

        return CheckInResultResponse(
            tokenAccepted = true,
            alreadyCheckedIn = false,
            checkedInAt = now,
            userId = userId,
            tierName = tierName,
            guestCount = guestCount,
        )
    }

    private fun requireOccurrenceForScanner(
        eventId: UUID,
        occurrenceId: UUID,
        scannerId: UUID,
    ): EventOccurrence {
        eventRepository.findByIdOrNull(eventId) ?: throw EventNotFoundException(eventId)
        val occurrence = eventOccurrenceService.getOccurrence(eventId, occurrenceId)
        val allowed = eventParticipantRepository.existsByIdOccurrenceIdAndIdUserIdAndRoleIn(
            occurrenceId,
            scannerId,
            scannerRoles,
        )
        if (!allowed) throw CheckInAccessDeniedException()
        return occurrence
    }

    private fun resolveBuffers(eventId: UUID, tierId: UUID?): Pair<Int, Int> {
        if (tierId == null) {
            return CheckInTokens.DEFAULT_BEFORE_MINUTES to CheckInTokens.DEFAULT_AFTER_MINUTES
        }
        val tier = eventTicketTierRepository.findByIdAndEventId(tierId, eventId).orElse(null)
            ?: return CheckInTokens.DEFAULT_BEFORE_MINUTES to CheckInTokens.DEFAULT_AFTER_MINUTES
        return tier.checkInBeforeMinutes to tier.checkInAfterMinutes
    }

    private fun isWithinWindow(
        occurrence: EventOccurrence,
        beforeMinutes: Int,
        afterMinutes: Int,
    ): Boolean {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val opens = occurrence.startTime.minusMinutes(beforeMinutes.toLong())
        val closes = occurrence.endTime.plusMinutes(afterMinutes.toLong())
        return !now.isBefore(opens) && !now.isAfter(closes)
    }

    private fun resolveTierName(eventId: UUID, tierId: UUID?): String? {
        if (tierId == null) return null
        return eventTicketTierRepository.findByIdAndEventId(tierId, eventId)
            .map(EventTicketTier::name)
            .orElse(null)
    }

    private fun ensureToken(reservation: Reservation): Boolean {
        if (reservation.status != ReservationStatus.ACCEPTED) return false
        if (!reservation.checkInToken.isNullOrBlank()) return false
        reservation.checkInToken = CheckInTokens.generate()
        return true
    }

    private fun failOrThrow(
        softFail: Boolean,
        errorCode: String,
        message: String,
        exception: () -> RuntimeException,
    ): CheckInResultResponse {
        if (!softFail) throw exception()
        return CheckInResultResponse(
            tokenAccepted = false,
            alreadyCheckedIn = false,
            checkedInAt = null,
            userId = null,
            tierName = null,
            guestCount = null,
            errorCode = errorCode,
            message = message,
        )
    }

    private fun Jwt.userId(): UUID =
        getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
