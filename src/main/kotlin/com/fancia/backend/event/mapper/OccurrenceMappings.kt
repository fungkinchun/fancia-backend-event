package com.fancia.backend.event.mapper

import com.fancia.backend.event.core.entity.EventOccurrence
import com.fancia.backend.shared.event.core.dto.EventOccurrenceResponse

fun EventOccurrence.toDto(): EventOccurrenceResponse {
    val eventId = event?.id ?: error("Occurrence ${id} is missing event reference")
    return EventOccurrenceResponse(
        id = id!!,
        eventId = eventId,
        startTime = startTime,
        endTime = endTime,
        status = status,
    )
}
