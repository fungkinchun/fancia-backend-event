package com.fancia.backend.event.core.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Duration
import java.time.LocalDateTime

data class UpdateEventRequest(
    @field:NotBlank(message = "Event description is required")
    val description: String,
    @field:NotNull(message = "Event start time is required")
    val startTime: LocalDateTime,
    @field:NotNull(message = "Event duration is required")
    @field:Positive(message = "Duration must be positive")
    val duration: Duration,
    val tags: Set<String>
)
