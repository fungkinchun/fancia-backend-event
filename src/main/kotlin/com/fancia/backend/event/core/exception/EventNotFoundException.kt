package com.fancia.backend.event.core.exception

import com.fancia.backend.shared.common.core.exception.DomainException
import java.util.*

class EventNotFoundException(
    val eventId: UUID,
    title: String = "Event not found",
    message: String = "Event not found with id: $eventId",
    errorCode: String = "EVENT_NOT_FOUND"
) : DomainException(title, message, errorCode)