package com.fancia.backend.event.core.dto

import com.fancia.backend.event.core.entity.ReservationStatus
import jakarta.validation.constraints.NotBlank
import java.util.*

data class UpdateReservationRequest(
    var guests: Int = 0,
    var payload: String,
    var status: ReservationStatus
)