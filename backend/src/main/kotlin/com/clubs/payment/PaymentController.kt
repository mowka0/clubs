package com.clubs.payment

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    @PostMapping("/create-invoice")
    fun createInvoice(
        @RequestBody request: CreateInvoiceRequest,
        authentication: Authentication
    ): ResponseEntity<CreateInvoiceResponse> {
        val userId = UUID.fromString(authentication.principal as String)
        val response = paymentService.createInvoice(userId, request.clubId)
        return ResponseEntity.ok(response)
    }
}
