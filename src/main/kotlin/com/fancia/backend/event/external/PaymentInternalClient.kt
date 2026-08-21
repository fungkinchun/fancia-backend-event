package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.payment.core.dto.ConnectCheckoutResponse
import com.fancia.backend.shared.payment.core.dto.CreateConnectCheckoutSessionRequest
import com.fancia.backend.shared.payment.core.dto.PayoutReadinessResponse
import com.fancia.backend.shared.payment.core.dto.RefundConnectCheckoutRequest
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.UUID

@FeignClient(
    name = "payment-internal-service",
    path = "/internal",
    configuration = [FeignConfig::class],
)
interface PaymentInternalClient {
    @GetMapping("/connect/accounts/{userId}")
    fun payoutReadiness(@PathVariable userId: UUID): PayoutReadinessResponse

    @PostMapping("/checkout/sessions")
    fun createCheckoutSession(
        @RequestBody request: CreateConnectCheckoutSessionRequest,
    ): ConnectCheckoutResponse

    @PostMapping("/checkout/refunds")
    fun refundCheckout(@RequestBody request: RefundConnectCheckoutRequest)
}
