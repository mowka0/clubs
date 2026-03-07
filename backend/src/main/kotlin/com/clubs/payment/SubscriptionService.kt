package com.clubs.payment

import com.clubs.bot.TelegramApiClient
import com.clubs.club.ClubRepository
import com.clubs.config.NotFoundException
import com.clubs.generated.jooq.enums.MembershipStatus
import com.clubs.membership.MembershipRepository
import com.clubs.notification.NotificationService
import com.clubs.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class SubscriptionService(
    private val membershipRepository: MembershipRepository,
    private val transactionRepository: TransactionRepository,
    private val clubRepository: ClubRepository,
    private val userService: UserService,
    private val notificationService: NotificationService,
    private val telegramApiClient: TelegramApiClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val SUBSCRIPTION_DURATION_DAYS = 30L
        const val GRACE_PERIOD_DAYS = 3L
        const val EXPIRY_WARNING_DAYS = 3L
        const val PLATFORM_FEE_PERCENT = 0.20
    }

    /**
     * Called when a Telegram Stars payment is successfully completed.
     * Extends the membership subscription by 30 days and records the transaction.
     */
    fun handleSuccessfulRenewal(
        membershipId: UUID,
        userId: UUID,
        clubId: UUID,
        amountStars: Int,
        telegramPaymentId: String
    ) {
        val membership = membershipRepository.findById(membershipId)
            ?: throw NotFoundException("Membership $membershipId not found")

        val currentExpiry = membership.subscriptionExpiresAt
        val base = if (currentExpiry != null && currentExpiry.isAfter(OffsetDateTime.now())) {
            currentExpiry
        } else {
            OffsetDateTime.now()
        }
        val newExpiresAt = base.plusDays(SUBSCRIPTION_DURATION_DAYS)
        membershipRepository.extendSubscription(membershipId, newExpiresAt)

        val platformFee = (amountStars * PLATFORM_FEE_PERCENT).toInt()
        val organizerRevenue = amountStars - platformFee
        transactionRepository.create(
            userId = userId,
            clubId = clubId,
            membershipId = membershipId,
            amountStars = amountStars,
            platformFee = platformFee,
            organizerRevenue = organizerRevenue,
            telegramPaymentId = telegramPaymentId
        )

        log.info("Subscription renewed: membership={} newExpiresAt={}", membershipId, newExpiresAt)
    }

    /**
     * Sends a renewal invoice link to the member via Telegram.
     * Uses the locked_subscription_price for grandfathering.
     */
    fun sendRenewalInvoice(membershipId: UUID) {
        val membership = membershipRepository.findById(membershipId) ?: return
        val price = membership.lockedSubscriptionPrice ?: return  // free club, no invoice needed
        if (price <= 0) return

        val club = clubRepository.findById(membership.clubId) ?: return
        val user = userService.findById(membership.userId) ?: return

        val invoicePayload = "${membership.clubId}_${membership.userId}"
        val invoiceLink = telegramApiClient.createInvoiceLink(
            title = "Продление подписки: ${club.name}",
            description = "Ежемесячная подписка на клуб «${club.name}»",
            payload = invoicePayload,
            amountStars = price
        )

        if (invoiceLink != null) {
            notificationService.sendPersonalNotification(
                telegramId = user.telegramId,
                text = "Продлите подписку на клуб <b>${club.name}</b> (${price} Stars/мес).",
                buttonText = "Оплатить",
                buttonUrl = invoiceLink
            )
            log.info("Renewal invoice sent: membership={} user={} price={}", membershipId, user.telegramId, price)
        } else {
            log.warn("Failed to create renewal invoice for membership={}", membershipId)
        }
    }

    /**
     * Transitions a membership from active to grace_period after subscription expires.
     */
    fun setGracePeriod(membershipId: UUID) {
        membershipRepository.updateStatusById(membershipId, MembershipStatus.grace_period)
        log.info("Membership {} moved to grace_period", membershipId)
    }

    /**
     * Expires a membership after grace period ends.
     */
    fun expireMembership(membershipId: UUID) {
        membershipRepository.updateStatusById(membershipId, MembershipStatus.expired)
        log.info("Membership {} expired", membershipId)
    }
}
