package com.clubs.membership

import com.clubs.club.ClubMemberDto
import com.clubs.generated.jooq.enums.MembershipRole
import com.clubs.generated.jooq.enums.MembershipStatus
import com.clubs.generated.jooq.tables.references.MEMBERSHIPS
import com.clubs.generated.jooq.tables.references.USERS
import com.clubs.generated.jooq.tables.references.USER_CLUB_REPUTATION
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class MembershipRepository(private val dsl: DSLContext) {

    fun create(
        userId: UUID,
        clubId: UUID,
        role: MembershipRole = MembershipRole.member,
        status: MembershipStatus = MembershipStatus.active,
        lockedSubscriptionPrice: Int? = null
    ): MembershipDto {
        val now = OffsetDateTime.now()
        val id = UUID.randomUUID()
        dsl.insertInto(MEMBERSHIPS)
            .set(MEMBERSHIPS.ID, id)
            .set(MEMBERSHIPS.USER_ID, userId)
            .set(MEMBERSHIPS.CLUB_ID, clubId)
            .set(MEMBERSHIPS.ROLE, role)
            .set(MEMBERSHIPS.STATUS, status)
            .set(MEMBERSHIPS.JOINED_AT, now)
            .set(MEMBERSHIPS.LOCKED_SUBSCRIPTION_PRICE, lockedSubscriptionPrice)
            .set(MEMBERSHIPS.CREATED_AT, now)
            .set(MEMBERSHIPS.UPDATED_AT, now)
            .execute()
        return findByUserAndClub(userId, clubId)!!
    }

    fun findByUserAndClub(userId: UUID, clubId: UUID): MembershipDto? {
        return dsl.selectFrom(MEMBERSHIPS)
            .where(MEMBERSHIPS.USER_ID.eq(userId))
            .and(MEMBERSHIPS.CLUB_ID.eq(clubId))
            .fetchOne()
            ?.toDto()
    }

    fun findByClub(clubId: UUID): List<MembershipDto> {
        return dsl.selectFrom(MEMBERSHIPS)
            .where(MEMBERSHIPS.CLUB_ID.eq(clubId))
            .fetch()
            .map { it.toDto() }
    }

    fun findActiveCountByClub(clubId: UUID): Int {
        return dsl.fetchCount(
            dsl.selectFrom(MEMBERSHIPS)
                .where(MEMBERSHIPS.CLUB_ID.eq(clubId))
                .and(MEMBERSHIPS.STATUS.eq(MembershipStatus.active))
        )
    }

    fun updateStatus(userId: UUID, clubId: UUID, status: MembershipStatus) {
        dsl.update(MEMBERSHIPS)
            .set(MEMBERSHIPS.STATUS, status)
            .set(MEMBERSHIPS.UPDATED_AT, OffsetDateTime.now())
            .where(MEMBERSHIPS.USER_ID.eq(userId))
            .and(MEMBERSHIPS.CLUB_ID.eq(clubId))
            .execute()
    }

    fun findMembersWithUsers(clubId: UUID): List<ClubMemberDto> {
        return dsl.select(
            MEMBERSHIPS.USER_ID,
            MEMBERSHIPS.ROLE,
            MEMBERSHIPS.JOINED_AT,
            USERS.USERNAME,
            USERS.FIRST_NAME,
            USERS.LAST_NAME,
            USERS.AVATAR_URL,
            USER_CLUB_REPUTATION.RELIABILITY_INDEX
        )
            .from(MEMBERSHIPS)
            .join(USERS).on(USERS.ID.eq(MEMBERSHIPS.USER_ID))
            .leftJoin(USER_CLUB_REPUTATION).on(
                USER_CLUB_REPUTATION.USER_ID.eq(MEMBERSHIPS.USER_ID)
                    .and(USER_CLUB_REPUTATION.CLUB_ID.eq(MEMBERSHIPS.CLUB_ID))
            )
            .where(MEMBERSHIPS.CLUB_ID.eq(clubId))
            .and(MEMBERSHIPS.STATUS.`in`(MembershipStatus.active, MembershipStatus.grace_period))
            .orderBy(MEMBERSHIPS.JOINED_AT.asc())
            .fetch()
            .map { record ->
                ClubMemberDto(
                    userId = record.get(MEMBERSHIPS.USER_ID)!!,
                    username = record.get(USERS.USERNAME),
                    firstName = record.get(USERS.FIRST_NAME),
                    lastName = record.get(USERS.LAST_NAME),
                    avatarUrl = record.get(USERS.AVATAR_URL),
                    role = record.get(MEMBERSHIPS.ROLE)!!.literal,
                    joinedAt = record.get(MEMBERSHIPS.JOINED_AT)!!,
                    reliabilityIndex = record.get(USER_CLUB_REPUTATION.RELIABILITY_INDEX) ?: 0
                )
            }
    }

    private fun org.jooq.Record.toDto(): MembershipDto = MembershipDto(
        id = get(MEMBERSHIPS.ID)!!,
        userId = get(MEMBERSHIPS.USER_ID)!!,
        clubId = get(MEMBERSHIPS.CLUB_ID)!!,
        role = get(MEMBERSHIPS.ROLE)!!.literal,
        status = get(MEMBERSHIPS.STATUS)!!.literal,
        joinedAt = get(MEMBERSHIPS.JOINED_AT)!!,
        subscriptionExpiresAt = get(MEMBERSHIPS.SUBSCRIPTION_EXPIRES_AT),
        lockedSubscriptionPrice = get(MEMBERSHIPS.LOCKED_SUBSCRIPTION_PRICE),
        createdAt = get(MEMBERSHIPS.CREATED_AT)!!,
        updatedAt = get(MEMBERSHIPS.UPDATED_AT)!!
    )
}
