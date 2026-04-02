package com.fancia.backend.event.core.dto

import jakarta.validation.constraints.NotBlank
import java.util.*

data class CreateReservationRequest(
    @field:NotBlank(message = "Event ID is required")
    val eventId: UUID,
    var guests: Int = 0,
    var payload: String,
)