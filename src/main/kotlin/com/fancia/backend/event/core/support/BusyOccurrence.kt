package com.fancia.backend.event.core.support

import java.time.LocalDateTime
import java.util.UUID

data class BusyOccurrence(
    val eventId: UUID,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
)
