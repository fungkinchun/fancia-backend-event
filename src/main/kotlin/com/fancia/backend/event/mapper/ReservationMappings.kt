package com.fancia.backend.event.mapper

import com.fancia.backend.event.core.entity.Reservation
import com.fancia.backend.event.core.entity.ReservationId
import com.fancia.backend.shared.event.core.dto.CreateReservationRequest
import com.fancia.backend.shared.event.core.dto.ReservationResponse
import com.fancia.backend.shared.event.core.dto.UpdateReservationRequest

fun Reservation.toDto(): ReservationResponse =
    ReservationResponse(
        eventId = id?.eventId,
        userId = id?.userId,
        status = status,
    )

fun CreateReservationRequest.toEntity(): Reservation =
    Reservation().apply {
        guests = guests
        payload = payload
    }

fun UpdateReservationRequest.toEntity(reservation: Reservation): Reservation {
    reservation.guests = guests
    reservation.payload = payload
    reservation.status = status
    return reservation
}

fun ReservationResponse.toEntity(): Reservation =
    Reservation(
        id = ReservationId(
            eventId = eventId,
            userId = userId,
        ),
    ).apply {
        status = status
    }
