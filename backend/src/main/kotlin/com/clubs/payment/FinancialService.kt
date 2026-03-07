package com.clubs.payment

import com.clubs.club.ClubRepository
import com.clubs.config.NotFoundException
import com.clubs.generated.jooq.enums.MembershipRole
import com.clubs.generated.jooq.enums.MembershipStatus
import com.clubs.membership.MembershipRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.YearMonth
import java.util.UUID

@Service
class FinancialService(
    private val clubRepository: ClubRepository,
    private val membershipRepository: MembershipRepository,
    private val transactionRepository: TransactionRepository
) {

    fun getFinancialStats(clubId: UUID, requesterId: UUID, month: YearMonth?): FinancialStatsDto {
        val club = clubRepository.findById(clubId) ?: throw NotFoundException("Club not found")
        requireOrganizer(clubId, requesterId)

        val transactions = if (month != null) {
            transactionRepository.findByClubForMonth(clubId, month)
        } else {
            val currentMonth = YearMonth.now()
            transactionRepository.findByClubForMonth(clubId, currentMonth)
        }

        val monthlyRevenue = transactions.sumOf { it.amountStars }
        val organizerShare = transactions.sumOf { it.organizerRevenue }
        val platformShare = transactions.sumOf { it.platformFee }
        val activeMembers = membershipRepository.findActiveCountByClub(clubId)

        val nextBillingDate = membershipRepository.findByClub(clubId)
            .filter { it.status == MembershipStatus.active.literal }
            .mapNotNull { it.subscriptionExpiresAt }
            .minOrNull()

        return FinancialStatsDto(
            activeMembers = activeMembers,
            monthlyRevenueStars = monthlyRevenue,
            organizerShare = organizerShare,
            platformShare = platformShare,
            nextBillingDate = nextBillingDate
        )
    }

    fun getTransactions(clubId: UUID, requesterId: UUID, page: Int, size: Int): PagedTransactionsResponse {
        clubRepository.findById(clubId) ?: throw NotFoundException("Club not found")
        requireOrganizer(clubId, requesterId)

        val transactions = transactionRepository.findByClubPaged(clubId, page, size)
        val total = transactionRepository.countByClub(clubId)

        return PagedTransactionsResponse(
            transactions = transactions,
            total = total,
            page = page,
            size = size
        )
    }

    private fun requireOrganizer(clubId: UUID, userId: UUID) {
        val membership = membershipRepository.findByUserAndClub(userId, clubId)
        if (membership == null || membership.role != MembershipRole.organizer.literal) {
            throw AccessDeniedException("Only organizer can access financial data")
        }
    }
}
