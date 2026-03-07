package com.clubs.payment

import java.time.OffsetDateTime
import java.util.UUID

data class TransactionDto(
    val id: UUID,
    val userId: UUID,
    val clubId: UUID,
    val membershipId: UUID?,
    val amountStars: Int,
    val platformFee: Int,
    val organizerRevenue: Int,
    val telegramPaymentId: String?,
    val transactionType: String,
    val createdAt: OffsetDateTime
)

data class CreateInvoiceRequest(
    val clubId: UUID
)

data class CreateInvoiceResponse(
    val invoiceLink: String
)

data class InvoicePayload(
    val clubId: UUID,
    val userId: UUID
)

data class FinancialStatsDto(
    val activeMembers: Int,
    val monthlyRevenueStars: Int,
    val organizerShare: Int,
    val platformShare: Int,
    val nextBillingDate: OffsetDateTime?
)

data class PagedTransactionsResponse(
    val transactions: List<TransactionDto>,
    val total: Int,
    val page: Int,
    val size: Int
)
