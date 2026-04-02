package com.fancia.backend.event.core.exception

import com.fancia.backend.shared.common.core.exception.DomainException
import java.util.*

class ReservationChangeDeniedException(
    val eventId: UUID,
    val userId: UUID,
    title: String = "Access denied to change event reservation",
    message: String = "Only admins or the member themselves can change reservation of event $eventId for user $userId",
    errorCode: String = "EVENT_RESERVATION_CHANGE_DENIED"
) : DomainException(title, message, errorCode)