package com.fancia.backend.event.mapper

import com.fancia.backend.event.core.dto.CreateEventRequest
import com.fancia.backend.event.core.dto.EventResponse
import com.fancia.backend.event.core.dto.UpdateEventRequest
import com.fancia.backend.event.core.entity.Event
import org.mapstruct.Mapper
import org.mapstruct.MappingTarget
import org.mapstruct.NullValueMappingStrategy

@Mapper(
    componentModel = "spring",
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT
)
interface EventMapper {
    fun toDto(event: Event): EventResponse
    fun toBean(event: CreateEventRequest): Event
    fun toBean(event: UpdateEventRequest): Event
    fun toBean(request: UpdateEventRequest, @MappingTarget target: Event): Event
}