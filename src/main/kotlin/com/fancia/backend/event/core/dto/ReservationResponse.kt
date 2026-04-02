package com.fancia.backend.event.core.dto

import com.fancia.backend.event.core.entity.ReservationStatus
import java.util.*

data class ReservationResponse(
    var eventId: UUID? = null,
    var userId: UUID? = null,
    var status: ReservationStatus? = null
)
