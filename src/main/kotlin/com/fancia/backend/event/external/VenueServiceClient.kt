package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.venue.core.dto.VenueResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.util.*

@FeignClient(name = "venue-service", path = "/api", configuration = [FeignConfig::class])
interface VenueServiceClient {
    @GetMapping("/venues/{id}")
    fun getVenue(@PathVariable id: UUID): VenueResponse
}
