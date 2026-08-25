package com.fancia.backend.event.mapper

import com.fancia.backend.event.core.entity.EventTicketTier
import com.fancia.backend.shared.event.core.dto.CreateEventTicketTierRequest
import com.fancia.backend.shared.event.core.dto.CreateReservationRequest
import com.fancia.backend.shared.event.core.dto.EventTicketTierResponse
import com.fancia.backend.shared.event.core.dto.ReservationResponse
import com.fancia.backend.shared.event.core.dto.UpdateEventTicketTierRequest
import com.fancia.backend.shared.event.core.dto.UpdateReservationRequest
import com.fancia.backend.shared.event.core.entity.Event
import com.fancia.backend.shared.event.core.entity.Reservation
import java.util.UUID

fun Reservation.toDto(
    eventId: UUID,
    includeCheckInToken: Boolean = false,
): ReservationResponse =
    ReservationResponse(
        eventId = eventId,
        occurrenceId = id?.occurrenceId,
        userId = id?.userId,
        status = status,
        guests = guests,
        payload = payload,
        tierId = tierId,
        priceMinor = priceMinor,
        currency = currency,
        checkedInAt = checkedInAt,
        checkInToken = if (includeCheckInToken) checkInToken else null,
    )

fun CreateReservationRequest.toEntity(): Reservation =
    Reservation().apply {
        guests = this@toEntity.guests
        payload = this@toEntity.payload
        tierId = this@toEntity.tierId
    }

fun UpdateReservationRequest.toEntity(reservation: Reservation): Reservation {
    reservation.guests = this@toEntity.guests
    reservation.payload = this@toEntity.payload
    reservation.status = this@toEntity.status
    return reservation
}

fun EventTicketTier.toDto(): EventTicketTierResponse =
    EventTicketTierResponse(
        id = id,
        eventId = event!!.id!!,
        name = name,
        priceMinor = priceMinor,
        currency = currency,
        capacityPerOccurrence = capacityPerOccurrence,
        checkInBeforeMinutes = checkInBeforeMinutes,
        checkInAfterMinutes = checkInAfterMinutes,
        sortOrder = sortOrder,
        createdBy = createdBy,
        createdAt = createdAt,
    )

fun CreateEventTicketTierRequest.toEntity(event: Event): EventTicketTier =
    EventTicketTier().apply {
        this.event = event
        name = this@toEntity.name.trim()
        priceMinor = this@toEntity.priceMinor
        currency = this@toEntity.currency.trim().lowercase()
        capacityPerOccurrence = this@toEntity.capacityPerOccurrence
        checkInBeforeMinutes = this@toEntity.checkInBeforeMinutes
        checkInAfterMinutes = this@toEntity.checkInAfterMinutes
        sortOrder = this@toEntity.sortOrder
    }

fun UpdateEventTicketTierRequest.applyTo(tier: EventTicketTier): EventTicketTier {
    name?.let { tier.name = it.trim() }
    priceMinor?.let { tier.priceMinor = it }
    currency?.let { tier.currency = it.trim().lowercase() }
    sortOrder?.let { tier.sortOrder = it }
    checkInBeforeMinutes?.let { tier.checkInBeforeMinutes = it }
    checkInAfterMinutes?.let { tier.checkInAfterMinutes = it }
    when {
        clearCapacity -> tier.capacityPerOccurrence = null
        capacityPerOccurrence != null -> tier.capacityPerOccurrence = capacityPerOccurrence
    }
    return tier
}
