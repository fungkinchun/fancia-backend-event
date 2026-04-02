package com.fancia.backend.event.core.exception

import com.fancia.backend.shared.common.core.exception.DomainException
import java.util.*

class ReservationNotFoundException(
    val eventId: UUID,
    val userId: UUID,
    title: String = "Reservation not found",
    message: String = "Reservation not found with eventId: $eventId userId: $userId",
    errorCode: String = "RESERVATION_NOT_FOUND"
) : DomainException(title, message, errorCode)
