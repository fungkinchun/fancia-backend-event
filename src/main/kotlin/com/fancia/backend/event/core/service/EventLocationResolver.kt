package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.support.EventLocationSupport
import com.fancia.backend.event.external.VenueServiceClient
import com.fancia.backend.shared.event.core.dto.EventLocationDto
import com.fancia.backend.shared.event.core.enums.EventLocationKind
import com.fancia.backend.shared.event.core.exception.EventVenueIdRequiredException
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import org.springframework.stereotype.Component
import java.util.*

@Component
class EventLocationResolver(
    private val venueServiceClient: VenueServiceClient,
) {
    fun apply(event: Event, location: EventLocationDto?) {
        if (location == null) {
            EventLocationSupport.clear(event)
            return
        }
        when (location.kind) {
            EventLocationKind.ONLINE -> EventLocationSupport.applyOnline(event)
            EventLocationKind.ADDRESS -> EventLocationSupport.applyAddress(event, location)
            EventLocationKind.VENUE -> {
                val venueId = location.venueId ?: throw EventVenueIdRequiredException()
                val venue = try {
                    venueServiceClient.getVenue(venueId)
                } catch (_: Exception) {
                    throw VenueNotFoundException(venueId)
                }
                if (venue.id == null) {
                    throw VenueNotFoundException(venueId)
                }
                EventLocationSupport.applyFromVenue(event, venueId, venue)
            }
        }
    }
}
