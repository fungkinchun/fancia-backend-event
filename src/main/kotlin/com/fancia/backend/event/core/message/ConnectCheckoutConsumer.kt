package com.fancia.backend.event.core.message

import com.fancia.backend.event.core.service.ReservationService
import com.fancia.backend.event.external.PaymentInternalClient
import com.fancia.backend.shared.payment.core.dto.RefundConnectCheckoutRequest
import com.fancia.backend.shared.payment.core.enums.ConnectCheckoutPurpose
import com.fancia.backend.shared.payment.core.message.ConnectCheckoutCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ConnectCheckoutConsumer(
    private val reservationService: ReservationService,
    private val paymentInternalClient: PaymentInternalClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["connect-checkouts"], groupId = "event-connect-checkout")
    fun onConnectCheckoutCompleted(event: ConnectCheckoutCompletedEvent) {
        if (event.purpose != ConnectCheckoutPurpose.EVENT_TICKET.name) return
        val ids = ReservationService.parseResourceId(event.resourceId)
            ?: run {
                log.warn("Ignoring event checkout with invalid resourceId={}", event.resourceId)
                return
            }
        val (eventId, occurrenceId, userId) = ids
        try {
            reservationService.confirmPaid(
                eventId = eventId,
                occurrenceId = occurrenceId,
                userId = userId,
                checkoutSessionId = event.checkoutSessionId,
            )
        } catch (ex: Exception) {
            log.error(
                "Ticket fulfill failed eventId={} occurrenceId={} userId={} session={} — refunding",
                eventId,
                occurrenceId,
                userId,
                event.checkoutSessionId,
                ex,
            )
            runCatching {
                paymentInternalClient.refundCheckout(
                    RefundConnectCheckoutRequest(event.checkoutSessionId),
                )
            }.onFailure { refundEx ->
                log.error("Refund failed for session={}", event.checkoutSessionId, refundEx)
            }
        }
    }
}
