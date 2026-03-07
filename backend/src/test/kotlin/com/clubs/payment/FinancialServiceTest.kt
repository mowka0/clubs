package com.clubs.payment

import com.clubs.club.ClubDto
import com.clubs.club.ClubRepository
import com.clubs.config.NotFoundException
import com.clubs.generated.jooq.enums.MembershipRole
import com.clubs.membership.MembershipDto
import com.clubs.membership.MembershipRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.access.AccessDeniedException
import java.time.OffsetDateTime
import java.time.YearMonth
import java.util.UUID

class FinancialServiceTest {

    private lateinit var clubRepository: ClubRepository
    private lateinit var membershipRepository: MembershipRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var service: FinancialService

    private val clubId = UUID.randomUUID()
    private val organizerId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val now = OffsetDateTime.now()

    private fun makeClub() = ClubDto(
        id = clubId, ownerId = organizerId, name = "Test Club", description = null,
        city = "Moscow", category = "sport", accessType = "open",
        memberLimit = 50, confirmedCount = 5, subscriptionPrice = 100,
        rules = null, applicationQuestion = null, avatarUrl = null, coverUrl = null,
        telegramGroupId = null, activityRating = 1.0, isActive = true,
        createdAt = now, updatedAt = now, promoTags = emptyList(), goingCount = 0
    )

    private fun makeOrganizerMembership() = MembershipDto(
        id = UUID.randomUUID(), userId = organizerId, clubId = clubId,
        role = MembershipRole.organizer.literal, status = "active",
        joinedAt = now, subscriptionExpiresAt = null,
        lockedSubscriptionPrice = null, createdAt = now, updatedAt = now
    )

    private fun makeMemberMembership() = MembershipDto(
        id = UUID.randomUUID(), userId = memberId, clubId = clubId,
        role = MembershipRole.member.literal, status = "active",
        joinedAt = now, subscriptionExpiresAt = now.plusDays(25),
        lockedSubscriptionPrice = 100, createdAt = now, updatedAt = now
    )

    private fun makeTransaction(amount: Int = 100) = TransactionDto(
        id = UUID.randomUUID(), userId = memberId, clubId = clubId,
        membershipId = null, amountStars = amount,
        platformFee = amount / 5, organizerRevenue = amount - amount / 5,
        telegramPaymentId = "pay_123", transactionType = "subscription",
        createdAt = now
    )

    @BeforeEach
    fun setUp() {
        clubRepository = mock()
        membershipRepository = mock()
        transactionRepository = mock()
        service = FinancialService(clubRepository, membershipRepository, transactionRepository)
    }

    @Test
    fun `getFinancialStats - club not found throws NotFoundException`() {
        whenever(clubRepository.findById(clubId)).thenReturn(null)
        assertThrows<NotFoundException> {
            service.getFinancialStats(clubId, organizerId, null)
        }
    }

    @Test
    fun `getFinancialStats - non-organizer throws AccessDeniedException`() {
        whenever(clubRepository.findById(clubId)).thenReturn(makeClub())
        whenever(membershipRepository.findByUserAndClub(memberId, clubId)).thenReturn(makeMemberMembership())
        assertThrows<AccessDeniedException> {
            service.getFinancialStats(clubId, memberId, null)
        }
    }

    @Test
    fun `getFinancialStats - returns correct stats for current month`() {
        val tx1 = makeTransaction(100)
        val tx2 = makeTransaction(200)
        whenever(clubRepository.findById(clubId)).thenReturn(makeClub())
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())
        whenever(transactionRepository.findByClubForMonth(eq(clubId), any())).thenReturn(listOf(tx1, tx2))
        whenever(membershipRepository.findActiveCountByClub(clubId)).thenReturn(5)
        whenever(membershipRepository.findByClub(clubId)).thenReturn(listOf(makeOrganizerMembership(), makeMemberMembership()))

        val stats = service.getFinancialStats(clubId, organizerId, null)

        assertEquals(5, stats.activeMembers)
        assertEquals(300, stats.monthlyRevenueStars)
        assertEquals(tx1.organizerRevenue + tx2.organizerRevenue, stats.organizerShare)
        assertEquals(tx1.platformFee + tx2.platformFee, stats.platformShare)
        assertNotNull(stats.nextBillingDate)
    }

    @Test
    fun `getFinancialStats - uses provided month filter`() {
        val month = YearMonth.of(2026, 3)
        whenever(clubRepository.findById(clubId)).thenReturn(makeClub())
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())
        whenever(transactionRepository.findByClubForMonth(clubId, month)).thenReturn(emptyList())
        whenever(membershipRepository.findActiveCountByClub(clubId)).thenReturn(0)
        whenever(membershipRepository.findByClub(clubId)).thenReturn(emptyList())

        val stats = service.getFinancialStats(clubId, organizerId, month)

        assertEquals(0, stats.monthlyRevenueStars)
        verify(transactionRepository).findByClubForMonth(clubId, month)
    }

    @Test
    fun `getFinancialStats - nextBillingDate is null when no active subscriptions`() {
        whenever(clubRepository.findById(clubId)).thenReturn(makeClub())
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())
        whenever(transactionRepository.findByClubForMonth(eq(clubId), any())).thenReturn(emptyList())
        whenever(membershipRepository.findActiveCountByClub(clubId)).thenReturn(0)
        whenever(membershipRepository.findByClub(clubId)).thenReturn(listOf(makeOrganizerMembership()))

        val stats = service.getFinancialStats(clubId, organizerId, null)

        assertNull(stats.nextBillingDate)
    }

    @Test
    fun `getTransactions - club not found throws NotFoundException`() {
        whenever(clubRepository.findById(clubId)).thenReturn(null)
        assertThrows<NotFoundException> {
            service.getTransactions(clubId, organizerId, 0, 20)
        }
    }

    @Test
    fun `getTransactions - non-organizer throws AccessDeniedException`() {
        whenever(clubRepository.findById(clubId)).thenReturn(makeClub())
        whenever(membershipRepository.findByUserAndClub(memberId, clubId)).thenReturn(makeMemberMembership())
        assertThrows<AccessDeniedException> {
            service.getTransactions(clubId, memberId, 0, 20)
        }
    }

    @Test
    fun `getTransactions - returns paged transactions`() {
        val txList = listOf(makeTransaction(100), makeTransaction(200))
        whenever(clubRepository.findById(clubId)).thenReturn(makeClub())
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())
        whenever(transactionRepository.findByClubPaged(clubId, 0, 20)).thenReturn(txList)
        whenever(transactionRepository.countByClub(clubId)).thenReturn(2)

        val result = service.getTransactions(clubId, organizerId, 0, 20)

        assertEquals(2, result.transactions.size)
        assertEquals(2, result.total)
        assertEquals(0, result.page)
        assertEquals(20, result.size)
    }
}
