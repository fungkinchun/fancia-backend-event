package com.fancia.backend.event.mapper

import com.fancia.backend.event.core.dto.CreateReservationRequest
import com.fancia.backend.event.core.dto.ReservationResponse
import com.fancia.backend.event.core.dto.UpdateReservationRequest
import com.fancia.backend.event.core.entity.Reservation
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.NullValueMappingStrategy
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = "spring",
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
interface ReservationMapper {
    @Mapping(target = "eventId", source = "id.eventId")
    @Mapping(target = "userId", source = "id.userId")
    fun toDto(reservation: Reservation): ReservationResponse

    @Mapping(target = "event", ignore = true)
    fun toBean(reservation: CreateReservationRequest): Reservation
    fun toBean(reservation: UpdateReservationRequest): Reservation
    fun toBean(request: UpdateReservationRequest, @MappingTarget target: Reservation): Reservation
    @Mapping(target = "id.eventId", source = "eventId")
    @Mapping(target = "id.userId", source = "userId")
    fun toBean(response: ReservationResponse): Reservation
}