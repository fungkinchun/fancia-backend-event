package com.fancia.backend.event.core.support

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.shared.event.core.dto.EventLocationDto
import com.fancia.backend.shared.event.core.enums.EventLocationKind
import com.fancia.backend.shared.venue.core.dto.VenueResponse

object EventLocationSupport {
    fun applyAddress(event: Event, location: EventLocationDto) {
        event.locationKind = location.kind
        event.venueId = null
        event.locationLabel = location.label
        event.placeId = location.placeId
        event.latitude = location.latitude
        event.longitude = location.longitude
        event.addressLine = location.addressLine
        event.city = location.city
        event.postcode = location.postcode
        event.country = location.country
    }

    fun applyFromVenue(event: Event, venueId: java.util.UUID, venue: VenueResponse) {
        event.locationKind = EventLocationKind.VENUE
        event.venueId = venueId
        val location = venue.location
        event.locationLabel = location?.label ?: venue.name
        event.placeId = location?.placeId
        event.latitude = location?.latitude
        event.longitude = location?.longitude
        event.addressLine = location?.addressLine
        event.city = location?.city
        event.postcode = location?.postcode
        event.country = location?.country
    }

    fun applyOnline(event: Event) {
        event.locationKind = EventLocationKind.ONLINE
        event.venueId = null
        clearCoordinates(event)
    }

    fun clear(event: Event) {
        event.locationKind = null
        event.venueId = null
        clearCoordinates(event)
    }

    private fun clearCoordinates(event: Event) {
        event.locationLabel = null
        event.placeId = null
        event.latitude = null
        event.longitude = null
        event.addressLine = null
        event.city = null
        event.postcode = null
        event.country = null
    }

    fun toDto(event: Event): EventLocationDto? {
        if (event.locationKind == null) {
            return null
        }
        return EventLocationDto(
            kind = event.locationKind!!,
            venueId = event.venueId,
            label = event.locationLabel,
            placeId = event.placeId,
            latitude = event.latitude,
            longitude = event.longitude,
            addressLine = event.addressLine,
            city = event.city,
            postcode = event.postcode,
            country = event.country,
        )
    }
}
