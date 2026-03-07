package com.clubs.payment

import com.clubs.club.ClubRepository
import com.clubs.notification.NotificationService
import com.clubs.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class SubscriptionScheduler(
    private val membershipRepository: com.clubs.membership.MembershipRepository,
    private val subscriptionService: SubscriptionService,
    private val clubRepository: ClubRepository,
    private val userService: UserService,
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Daily: check subscriptions expiring in 3 days and send warning notifications.
     */
    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000L)
    fun notifyExpiringSoon() {
        val now = OffsetDateTime.now()
        val cutoff = now.plusDays(SubscriptionService.EXPIRY_WARNING_DAYS)
        // Find memberships expiring between now and now+3 days (not yet expired)
        val expiringSoon = membershipRepository.findActiveExpiringSoon(cutoff)
            .filter { it.subscriptionExpiresAt!!.isAfter(now) }

        if (expiringSoon.isEmpty()) return

        expiringSoon.forEach { membership ->
            try {
                val user = userService.findById(membership.userId) ?: return@forEach
                val club = clubRepository.findById(membership.clubId) ?: return@forEach
                val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(
                    now.toLocalDate(), membership.subscriptionExpiresAt!!.toLocalDate()
                ).coerceAtLeast(1).toInt()
                notificationService.notifySubscriptionExpiringSoon(
                    telegramId = user.telegramId,
                    clubName = club.name,
                    clubId = club.id,
                    daysLeft = daysLeft
                )
            } catch (e: Exception) {
                log.error("Failed to notify expiring subscription for membership={}: {}", membership.id, e.message, e)
            }
        }
        log.info("Sent expiry warning to ${expiringSoon.size} member(s)")
    }

    /**
     * Daily: move active memberships with expired subscriptions to grace_period
     * and send renewal invoice.
     */
    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000L)
    fun transitionExpiredToGracePeriod() {
        val now = OffsetDateTime.now()
        val expired = membershipRepository.findActiveExpired(now)

        if (expired.isEmpty()) return

        expired.forEach { membership ->
            try {
                subscriptionService.setGracePeriod(membership.id)
                subscriptionService.sendRenewalInvoice(membership.id)
            } catch (e: Exception) {
                log.error("Failed to transition membership={} to grace_period: {}", membership.id, e.message, e)
            }
        }
        log.info("Moved ${expired.size} membership(s) to grace_period")
    }

    /**
     * Daily: expire memberships where grace period has ended
     * (subscription_expires_at + 3 days <= now).
     */
    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000L)
    fun expireGracePeriodMemberships() {
        val graceCutoff = OffsetDateTime.now().minusDays(SubscriptionService.GRACE_PERIOD_DAYS)
        val toExpire = membershipRepository.findGracePeriodExpired(graceCutoff)

        if (toExpire.isEmpty()) return

        toExpire.forEach { membership ->
            try {
                subscriptionService.expireMembership(membership.id)
            } catch (e: Exception) {
                log.error("Failed to expire membership={}: {}", membership.id, e.message, e)
            }
        }
        log.info("Expired ${toExpire.size} membership(s) after grace period")
    }
}
