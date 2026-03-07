package com.clubs.payment

import java.util.UUID

interface PaymentService {
    fun createInvoice(userId: UUID, clubId: UUID): CreateInvoiceResponse
    fun handleSuccessfulPayment(
        userId: UUID,
        clubId: UUID,
        amountStars: Int,
        telegramPaymentId: String,
        membershipId: UUID? = null
    ): TransactionDto
    fun validatePreCheckoutQuery(clubId: UUID, amountStars: Int): Boolean
}
