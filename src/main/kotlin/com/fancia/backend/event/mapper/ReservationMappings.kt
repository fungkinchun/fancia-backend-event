package com.fancia.backend.event.mapper

import com.fancia.backend.event.core.entity.Reservation
import com.fancia.backend.event.core.entity.ReservationId
import com.fancia.backend.shared.event.core.dto.CreateReservationRequest
import com.fancia.backend.shared.event.core.dto.ReservationResponse
import com.fancia.backend.shared.event.core.dto.UpdateReservationRequest
import java.util.UUID

fun Reservation.toDto(eventId: UUID): ReservationResponse =
    ReservationResponse(
        eventId = eventId,
        occurrenceId = id?.occurrenceId,
        userId = id?.userId,
        status = status,
    )

fun CreateReservationRequest.toEntity(): Reservation =
    Reservation().apply {
    }

fun UpdateReservationRequest.toEntity(reservation: Reservation): Reservation {
    reservation.guests = this@toEntity.guests
    reservation.payload = this@toEntity.payload
    reservation.status = this@toEntity.status
    return reservation
}
