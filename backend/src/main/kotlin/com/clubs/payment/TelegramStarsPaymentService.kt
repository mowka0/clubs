package com.clubs.payment

import com.clubs.bot.TelegramApiClient
import com.clubs.club.ClubRepository
import com.clubs.config.NotFoundException
import com.clubs.membership.MembershipRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TelegramStarsPaymentService(
    private val telegramApiClient: TelegramApiClient,
    private val transactionRepository: TransactionRepository,
    private val clubRepository: ClubRepository,
    private val membershipRepository: MembershipRepository
) : PaymentService {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val PLATFORM_FEE_PERCENT = 0.20
        const val STARS_CURRENCY = "XTR"
    }

    override fun createInvoice(userId: UUID, clubId: UUID): CreateInvoiceResponse {
        val club = clubRepository.findById(clubId)
            ?: throw NotFoundException("Club $clubId not found")

        val invoicePayload = "${clubId}_${userId}"
        val invoiceLink = telegramApiClient.createInvoiceLink(
            title = "Подписка: ${club.name}",
            description = "Ежемесячная подписка на клуб «${club.name}»",
            payload = invoicePayload,
            amountStars = club.subscriptionPrice
        ) ?: throw IllegalStateException("Failed to create invoice link")

        return CreateInvoiceResponse(invoiceLink = invoiceLink)
    }

    override fun handleSuccessfulPayment(
        userId: UUID,
        clubId: UUID,
        amountStars: Int,
        telegramPaymentId: String,
        membershipId: UUID?
    ): TransactionDto {
        val resolvedMembershipId = membershipId
            ?: membershipRepository.findByUserAndClub(userId, clubId)?.id

        val platformFee = (amountStars * PLATFORM_FEE_PERCENT).toInt()
        val organizerRevenue = amountStars - platformFee

        val transaction = transactionRepository.create(
            userId = userId,
            clubId = clubId,
            membershipId = resolvedMembershipId,
            amountStars = amountStars,
            platformFee = platformFee,
            organizerRevenue = organizerRevenue,
            telegramPaymentId = telegramPaymentId
        )

        log.info(
            "Payment recorded: user={} club={} amount={} Stars telegramPaymentId={}",
            userId, clubId, amountStars, telegramPaymentId
        )
        return transaction
    }

    override fun validatePreCheckoutQuery(clubId: UUID, amountStars: Int): Boolean {
        val club = clubRepository.findById(clubId) ?: return false
        return club.subscriptionPrice == amountStars
    }

    fun parseInvoicePayload(payload: String): Pair<UUID, UUID>? {
        return try {
            val parts = payload.split("_")
            if (parts.size != 2) return null
            val clubId = UUID.fromString(parts[0])
            val userId = UUID.fromString(parts[1])
            Pair(clubId, userId)
        } catch (e: Exception) {
            log.warn("Failed to parse invoice payload: {}", payload)
            null
        }
    }
}
