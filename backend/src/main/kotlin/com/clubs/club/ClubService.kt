package com.clubs.club

import com.clubs.config.ValidationException
import com.clubs.generated.jooq.enums.MembershipRole
import com.clubs.generated.jooq.enums.MembershipStatus
import com.clubs.generated.jooq.tables.references.MEMBERSHIPS
import com.clubs.storage.FileStorageService
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ClubService(
    private val clubRepository: ClubRepository,
    private val dsl: DSLContext,
    @Autowired(required = false) private val fileStorageService: FileStorageService? = null
) {

    companion object {
        const val MAX_CLUBS_PER_ORGANIZER = 10
        const val MAX_NAME_LENGTH = 60
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MIN_MEMBER_LIMIT = 10
        const val MAX_MEMBER_LIMIT = 80
        const val PLATFORM_FEE_PERCENT = 0.20
    }

    fun createClub(
        ownerId: UUID,
        name: String,
        description: String?,
        city: String?,
        category: String,
        accessType: String,
        memberLimit: Int,
        subscriptionPrice: Int,
        rules: String? = null,
        applicationQuestion: String? = null,
        avatarBytes: ByteArray? = null,
        avatarFileName: String? = null
    ): ClubDto {
        validateClubFields(name, description, memberLimit)

        val existingCount = clubRepository.countByOwner(ownerId)
        if (existingCount >= MAX_CLUBS_PER_ORGANIZER) {
            throw ValidationException("Organizer cannot create more than $MAX_CLUBS_PER_ORGANIZER clubs")
        }

        val avatarUrl = uploadAvatarIfPresent(avatarBytes, avatarFileName)

        val club = clubRepository.create(
            CreateClubDto(
                ownerId = ownerId,
                name = name,
                description = description,
                city = city,
                category = category,
                accessType = accessType,
                memberLimit = memberLimit,
                subscriptionPrice = subscriptionPrice,
                avatarUrl = avatarUrl,
                rules = rules,
                applicationQuestion = applicationQuestion
            )
        )

        createOrganizerMembership(ownerId, club.id, subscriptionPrice)

        return club
    }

    fun calculateRevenue(price: Int, memberLimit: Int): MonthlyRevenueDto {
        val totalRevenue = price * memberLimit
        val platformShare = (totalRevenue * PLATFORM_FEE_PERCENT).toInt()
        val organizerShare = totalRevenue - platformShare
        return MonthlyRevenueDto(
            totalRevenue = totalRevenue,
            organizerShare = organizerShare,
            platformShare = platformShare
        )
    }

    private fun validateClubFields(name: String, description: String?, memberLimit: Int) {
        if (name.length > MAX_NAME_LENGTH) {
            throw ValidationException("Club name must be at most $MAX_NAME_LENGTH characters")
        }
        if (description != null && description.length > MAX_DESCRIPTION_LENGTH) {
            throw ValidationException("Club description must be at most $MAX_DESCRIPTION_LENGTH characters")
        }
        if (memberLimit < MIN_MEMBER_LIMIT || memberLimit > MAX_MEMBER_LIMIT) {
            throw ValidationException("Member limit must be between $MIN_MEMBER_LIMIT and $MAX_MEMBER_LIMIT")
        }
    }

    private fun uploadAvatarIfPresent(avatarBytes: ByteArray?, avatarFileName: String?): String? {
        if (avatarBytes == null || avatarFileName == null || fileStorageService == null) return null
        val key = "clubs/avatars/${UUID.randomUUID()}-$avatarFileName"
        return fileStorageService.uploadFile(avatarBytes, key)
    }

    private fun createOrganizerMembership(userId: UUID, clubId: UUID, subscriptionPrice: Int) {
        val now = OffsetDateTime.now()
        dsl.insertInto(MEMBERSHIPS)
            .set(MEMBERSHIPS.USER_ID, userId)
            .set(MEMBERSHIPS.CLUB_ID, clubId)
            .set(MEMBERSHIPS.ROLE, MembershipRole.organizer)
            .set(MEMBERSHIPS.STATUS, MembershipStatus.active)
            .set(MEMBERSHIPS.JOINED_AT, now)
            .set(MEMBERSHIPS.LOCKED_SUBSCRIPTION_PRICE, subscriptionPrice)
            .set(MEMBERSHIPS.CREATED_AT, now)
            .set(MEMBERSHIPS.UPDATED_AT, now)
            .execute()
    }
}
