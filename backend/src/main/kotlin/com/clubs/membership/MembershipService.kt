package com.clubs.membership

import com.clubs.club.ClubRepository
import com.clubs.config.ConflictException
import com.clubs.config.NotFoundException
import com.clubs.config.ValidationException
import com.clubs.generated.jooq.enums.MembershipStatus
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class MembershipService(
    private val membershipRepository: MembershipRepository,
    private val clubRepository: ClubRepository
) {

    fun joinClub(userId: UUID, clubId: UUID): MembershipDto {
        val club = clubRepository.findById(clubId)
            ?: throw NotFoundException("Club $clubId not found")

        val existing = membershipRepository.findByUserAndClub(userId, clubId)
        if (existing != null) {
            throw ConflictException("User is already a member of this club")
        }

        val activeCount = membershipRepository.findActiveCountByClub(clubId)
        if (activeCount >= club.memberLimit) {
            throw ValidationException("Club is full")
        }

        return membershipRepository.create(
            userId = userId,
            clubId = clubId,
            lockedSubscriptionPrice = club.subscriptionPrice
        )
    }

    fun isActiveMember(userId: UUID, clubId: UUID): Boolean {
        val membership = membershipRepository.findByUserAndClub(userId, clubId) ?: return false
        return when (membership.status) {
            MembershipStatus.active.literal -> true
            MembershipStatus.grace_period.literal -> true
            MembershipStatus.cancelled.literal -> {
                val expiresAt = membership.subscriptionExpiresAt
                expiresAt != null && expiresAt.isAfter(OffsetDateTime.now())
            }
            else -> false
        }
    }

    fun leaveClub(userId: UUID, clubId: UUID) {
        val membership = membershipRepository.findByUserAndClub(userId, clubId)
            ?: throw NotFoundException("Membership not found")
        if (membership.status == MembershipStatus.cancelled.literal) {
            throw ConflictException("Already left the club")
        }
        membershipRepository.updateStatus(userId, clubId, MembershipStatus.cancelled)
    }

    fun getMembership(userId: UUID, clubId: UUID): MembershipDto? {
        return membershipRepository.findByUserAndClub(userId, clubId)
    }
}
