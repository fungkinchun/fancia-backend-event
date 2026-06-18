package com.fancia.backend.event.mapper

import com.fancia.backend.event.core.entity.Reservation
import com.fancia.backend.event.core.entity.ReservationId
import com.fancia.backend.shared.event.core.dto.CreateReservationRequest
import com.fancia.backend.shared.event.core.dto.ReservationResponse
import com.fancia.backend.shared.event.core.dto.UpdateReservationRequest

fun Reservation.toDto(): ReservationResponse =
    ReservationResponse(
        eventId = this@toDto.id?.eventId,
        userId = this@toDto.id?.userId,
        status = this@toDto.status,
    )

fun CreateReservationRequest.toEntity(): Reservation =
    Reservation().apply {
        guests = this@toEntity.guests
        payload = this@toEntity.payload
    }

fun UpdateReservationRequest.toEntity(reservation: Reservation): Reservation {
    reservation.guests = this@toEntity.guests
    reservation.payload = this@toEntity.payload
    reservation.status = this@toEntity.status
    return reservation
}

fun ReservationResponse.toEntity(): Reservation =
    Reservation(
        id = ReservationId(
            eventId = this@toEntity.eventId,
            userId = this@toEntity.userId,
        ),
    ).apply {
        status = this@toEntity.status
    }
