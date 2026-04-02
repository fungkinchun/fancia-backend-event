package com.fancia.backend.event.core.exception

import com.fancia.backend.shared.common.core.exception.DomainException
import java.util.*

class ReservationAlreadyExistsException(
    val eventId: UUID,
    val userId: UUID,
    title: String = "Reservation already exists",
    message: String = "User $userId already reserved event $eventId",
    errorCode: String = "RESERVATION_ALREADY_EXISTS"
) : DomainException(title, message, errorCode)