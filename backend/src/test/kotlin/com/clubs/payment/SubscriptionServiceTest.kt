package com.clubs.payment

import com.clubs.bot.TelegramApiClient
import com.clubs.club.ClubDto
import com.clubs.club.ClubRepository
import com.clubs.config.NotFoundException
import com.clubs.generated.jooq.enums.MembershipRole
import com.clubs.generated.jooq.enums.MembershipStatus
import com.clubs.membership.MembershipDto
import com.clubs.membership.MembershipRepository
import com.clubs.notification.NotificationService
import com.clubs.user.UserDto
import com.clubs.user.UserService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.OffsetDateTime
import java.util.UUID

class SubscriptionServiceTest {

    private lateinit var membershipRepository: MembershipRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var clubRepository: ClubRepository
    private lateinit var userService: UserService
    private lateinit var notificationService: NotificationService
    private lateinit var telegramApiClient: TelegramApiClient
    private lateinit var service: SubscriptionService

    private val membershipId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val clubId = UUID.randomUUID()
    private val now = OffsetDateTime.now()

    private fun membership(
        status: MembershipStatus = MembershipStatus.active,
        expiresAt: OffsetDateTime? = now.plusDays(10),
        lockedPrice: Int? = 100
    ) = MembershipDto(
        id = membershipId,
        userId = userId,
        clubId = clubId,
        role = MembershipRole.member.literal,
        status = status.literal,
        joinedAt = now.minusDays(30),
        subscriptionExpiresAt = expiresAt,
        lockedSubscriptionPrice = lockedPrice,
        createdAt = now.minusDays(30),
        updatedAt = now
    )

    private fun user() = UserDto(
        id = userId,
        telegramId = 123456L,
        username = "testuser",
        firstName = "Test",
        lastName = null,
        avatarUrl = null,
        city = null,
        createdAt = now,
        updatedAt = now
    )

    private fun club() = ClubDto(
        id = clubId,
        ownerId = UUID.randomUUID(),
        name = "Test Club",
        description = "desc",
        city = "Moscow",
        category = "sport",
        accessType = "open",
        memberLimit = 50,
        subscriptionPrice = 100,
        avatarUrl = null,
        coverUrl = null,
        rules = null,
        applicationQuestion = null,
        telegramGroupId = null,
        activityRating = 0.0,
        confirmedCount = 5,
        isActive = true,
        createdAt = now,
        updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        membershipRepository = mock()
        transactionRepository = mock()
        clubRepository = mock()
        userService = mock()
        notificationService = mock()
        telegramApiClient = mock()
        service = SubscriptionService(
            membershipRepository, transactionRepository, clubRepository,
            userService, notificationService, telegramApiClient
        )
    }

    private fun stubTransaction() {
        whenever(transactionRepository.create(any(), any(), anyOrNull(), any(), any(), any(), anyOrNull(), any())).thenReturn(
            TransactionDto(UUID.randomUUID(), userId, clubId, membershipId, 100, 20, 80, "pay123", "subscription", now)
        )
    }

    @Test
    fun `handleSuccessfulRenewal extends subscription from current expiry`() {
        val currentExpiry = now.plusDays(5)
        whenever(membershipRepository.findById(membershipId)).thenReturn(membership(expiresAt = currentExpiry))
        stubTransaction()

        service.handleSuccessfulRenewal(membershipId, userId, clubId, 100, "pay123")

        verify(membershipRepository).extendSubscription(eq(membershipId), eq(currentExpiry.plusDays(30)))
    }

    @Test
    fun `handleSuccessfulRenewal uses now as base when subscription already expired`() {
        val pastExpiry = now.minusDays(2)
        whenever(membershipRepository.findById(membershipId)).thenReturn(membership(expiresAt = pastExpiry))
        stubTransaction()

        service.handleSuccessfulRenewal(membershipId, userId, clubId, 100, "pay123")

        val captor = argumentCaptor<OffsetDateTime>()
        verify(membershipRepository).extendSubscription(eq(membershipId), captor.capture())
        val diff = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), captor.firstValue.toLocalDate())
        assertEquals(30L, diff)
    }

    @Test
    fun `handleSuccessfulRenewal throws NotFoundException when membership missing`() {
        whenever(membershipRepository.findById(membershipId)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.handleSuccessfulRenewal(membershipId, userId, clubId, 100, "pay123")
        }
    }

    @Test
    fun `handleSuccessfulRenewal records transaction with 80-20 split`() {
        whenever(membershipRepository.findById(membershipId)).thenReturn(membership())
        stubTransaction()

        service.handleSuccessfulRenewal(membershipId, userId, clubId, 100, "pay123")

        verify(transactionRepository).create(
            any(), any(), anyOrNull(), eq(100), eq(20), eq(80), anyOrNull(), any()
        )
    }

    @Test
    fun `sendRenewalInvoice sends invoice and notifies user`() {
        whenever(membershipRepository.findById(membershipId)).thenReturn(membership())
        whenever(clubRepository.findById(clubId)).thenReturn(club())
        whenever(userService.findById(userId)).thenReturn(user())
        whenever(telegramApiClient.createInvoiceLink(any(), any(), any(), any())).thenReturn("https://t.me/invoice")

        service.sendRenewalInvoice(membershipId)

        verify(telegramApiClient).createInvoiceLink(any(), any(), any(), eq(100))
        verify(notificationService).sendPersonalNotification(
            telegramId = eq(123456L),
            text = any(),
            buttonText = any(),
            buttonUrl = eq("https://t.me/invoice")
        )
    }

    @Test
    fun `sendRenewalInvoice skips when lockedSubscriptionPrice is null (free club)`() {
        whenever(membershipRepository.findById(membershipId)).thenReturn(membership(lockedPrice = null))

        service.sendRenewalInvoice(membershipId)

        verifyNoInteractions(telegramApiClient)
        verifyNoInteractions(notificationService)
    }

    @Test
    fun `sendRenewalInvoice skips when lockedSubscriptionPrice is zero`() {
        whenever(membershipRepository.findById(membershipId)).thenReturn(membership(lockedPrice = 0))

        service.sendRenewalInvoice(membershipId)

        verifyNoInteractions(telegramApiClient)
        verifyNoInteractions(notificationService)
    }

    @Test
    fun `setGracePeriod updates status to grace_period`() {
        service.setGracePeriod(membershipId)

        verify(membershipRepository).updateStatusById(membershipId, MembershipStatus.grace_period)
    }

    @Test
    fun `expireMembership updates status to expired`() {
        service.expireMembership(membershipId)

        verify(membershipRepository).updateStatusById(membershipId, MembershipStatus.expired)
    }
}
