package com.fancia.backend.event.mapper

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.shared.event.core.dto.CreateEventRequest
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventRequest
import org.mapstruct.*

@Mapper(
    componentModel = "spring",
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
interface EventMapper {
    fun toDto(event: Event): EventResponse
    fun toBean(event: CreateEventRequest): Event
    fun toBean(event: UpdateEventRequest): Event
    fun toBean(response: EventResponse): Event
    fun toBean(request: UpdateEventRequest, @MappingTarget target: Event): Event

    @AfterMapping
    fun initializeCollections(@MappingTarget event: Event) {
        if (event.interestGroups == null) {
            event.interestGroups = mutableSetOf()
        }
        if (event.tags == null) {
            event.tags = mutableSetOf()
        }
    }
}