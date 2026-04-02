package com.fancia.backend.event.core.dto

import java.time.Duration
import java.time.LocalDateTime
import java.util.*

data class EventResponse(
    val id: UUID? = null,
    var name: String = "",
    var description: String = "",
    var userId: UUID? = null,
    var interestGroupId: UUID? = null,
    var createdAt: LocalDateTime? = null,
    var startTime: LocalDateTime? = null,
    var duration: Duration? = null,
    val tags: Set<String>
)