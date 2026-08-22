package com.fancia.backend.event.core.controller

import com.fancia.backend.event.core.service.ReservationService
import com.fancia.backend.shared.event.core.dto.EventReservationCheckoutSnapshot
import com.fancia.backend.shared.event.core.dto.ReservationResponse
import com.fancia.backend.shared.payment.core.dto.ConfirmConnectCheckoutPaidRequest
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/events/{eventId}/occurrences/{occurrenceId}/reservations/{userId}")
@Hidden
class ReservationInternalController(
    private val reservationService: ReservationService,
) {
    @Operation(summary = "Checkout snapshot for payment-service")
    @GetMapping("/checkout")
    fun checkoutSnapshot(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PathVariable userId: UUID,
    ): ResponseEntity<EventReservationCheckoutSnapshot> =
        ResponseEntity.ok(reservationService.checkoutSnapshot(eventId, occurrenceId, userId))

    @Operation(summary = "Confirm reservation paid after Stripe Checkout completes")
    @PostMapping("/paid")
    fun confirmPaid(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PathVariable userId: UUID,
        @RequestBody(required = false) request: ConfirmConnectCheckoutPaidRequest?,
    ): ResponseEntity<ReservationResponse> =
        ResponseEntity.ok(
            reservationService.confirmPaid(
                eventId = eventId,
                occurrenceId = occurrenceId,
                userId = userId,
                checkoutSessionId = request?.checkoutSessionId,
            ),
        )
}
