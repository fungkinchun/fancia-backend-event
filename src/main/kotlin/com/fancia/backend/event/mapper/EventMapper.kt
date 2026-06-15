package com.fancia.backend.event.mapper

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.support.EventLocationSupport
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
    @Mapping(target = "location", ignore = true)
    fun toDto(event: Event): EventResponse

    @Mapping(target = "links", ignore = true)
    @Mapping(target = "locationKind", ignore = true)
    @Mapping(target = "venueId", ignore = true)
    @Mapping(target = "locationLabel", ignore = true)
    @Mapping(target = "placeId", ignore = true)
    @Mapping(target = "latitude", ignore = true)
    @Mapping(target = "longitude", ignore = true)
    @Mapping(target = "addressLine", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "postcode", ignore = true)
    @Mapping(target = "country", ignore = true)
    fun toBean(event: CreateEventRequest): Event

    @Mapping(target = "links", ignore = true)
    @Mapping(target = "locationKind", ignore = true)
    @Mapping(target = "venueId", ignore = true)
    @Mapping(target = "locationLabel", ignore = true)
    @Mapping(target = "placeId", ignore = true)
    @Mapping(target = "latitude", ignore = true)
    @Mapping(target = "longitude", ignore = true)
    @Mapping(target = "addressLine", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "postcode", ignore = true)
    @Mapping(target = "country", ignore = true)
    fun toBean(event: UpdateEventRequest): Event

    fun toBean(response: EventResponse): Event

    @Mapping(target = "links", ignore = true)
    @Mapping(target = "locationKind", ignore = true)
    @Mapping(target = "venueId", ignore = true)
    @Mapping(target = "locationLabel", ignore = true)
    @Mapping(target = "placeId", ignore = true)
    @Mapping(target = "latitude", ignore = true)
    @Mapping(target = "longitude", ignore = true)
    @Mapping(target = "addressLine", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "postcode", ignore = true)
    @Mapping(target = "country", ignore = true)
    fun toBean(request: UpdateEventRequest, @MappingTarget target: Event): Event

    @AfterMapping
    fun initializeCollections(@MappingTarget event: Event) {
        if (event.interestGroups == null) {
            event.interestGroups = mutableSetOf()
        }
        if (event.tags == null) {
            event.tags = mutableSetOf()
        }
        if (event.links == null) {
            event.links = mutableSetOf()
        }
    }

    @AfterMapping
    fun mapLocationToDto(event: Event, @MappingTarget response: EventResponse) {
        response.location = EventLocationSupport.toDto(event)
    }
}
