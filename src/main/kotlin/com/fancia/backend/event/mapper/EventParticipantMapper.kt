package com.fancia.backend.event.mapper

import com.fancia.backend.event.core.entity.EventParticipant
import com.fancia.backend.shared.event.core.dto.EventParticipantResponse
import org.mapstruct.Mapper
import org.mapstruct.NullValueMappingStrategy
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = "spring",
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
interface EventParticipantMapper {
    fun toDto(participant: EventParticipant): EventParticipantResponse
    fun toBean(response: EventParticipantResponse): EventParticipant
}