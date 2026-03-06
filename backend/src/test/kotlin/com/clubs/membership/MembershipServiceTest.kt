package com.clubs.membership

import com.clubs.club.ClubDto
import com.clubs.club.ClubRepository
import com.clubs.config.ConflictException
import com.clubs.config.NotFoundException
import com.clubs.config.ValidationException
import com.clubs.generated.jooq.enums.MembershipStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.OffsetDateTime
import java.util.UUID

class MembershipServiceTest {

    private lateinit var membershipRepository: MembershipRepository
    private lateinit var clubRepository: ClubRepository
    private lateinit var service: MembershipService

    private val userId = UUID.randomUUID()
    private val clubId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        membershipRepository = mock()
        clubRepository = mock()
        service = MembershipService(membershipRepository, clubRepository)
    }

    private fun makeClub(memberLimit: Int = 20, subscriptionPrice: Int = 100): ClubDto {
        val now = OffsetDateTime.now()
        return ClubDto(
            id = clubId,
            ownerId = UUID.randomUUID(),
            name = "Test Club",
            description = null,
            city = null,
            category = "sport",
            accessType = "open",
            memberLimit = memberLimit,
            subscriptionPrice = subscriptionPrice,
            avatarUrl = null,
            coverUrl = null,
            rules = null,
            applicationQuestion = null,
            telegramGroupId = null,
            activityRating = 0.0,
            confirmedCount = 0,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun makeMembership(
        status: String = "active",
        subscriptionExpiresAt: OffsetDateTime? = null
    ): MembershipDto {
        val now = OffsetDateTime.now()
        return MembershipDto(
            id = UUID.randomUUID(),
            userId = userId,
            clubId = clubId,
            role = "member",
            status = status,
            joinedAt = now,
            subscriptionExpiresAt = subscriptionExpiresAt,
            lockedSubscriptionPrice = 100,
            createdAt = now,
            updatedAt = now
        )
    }

    // joinClub tests

    @Test
    fun `joinClub throws NotFoundException when club does not exist`() {
        whenever(clubRepository.findById(clubId)).thenReturn(null)

        assertThrows<NotFoundException> { service.joinClub(userId, clubId) }
    }

    @Test
    fun `joinClub throws ConflictException when already a member`() {
        whenever(clubRepository.findById(clubId)).thenReturn(makeClub())
        whenever(membershipRepository.findByUserAndClub(userId, clubId)).thenReturn(makeMembership())

        assertThrows<ConflictException> { service.joinClub(userId, clubId) }
    }

    @Test
    fun `joinClub throws ValidationException when club is full`() {
        whenever(clubRepository.findById(clubId)).thenReturn(makeClub(memberLimit = 10))
        whenever(membershipRepository.findByUserAndClub(userId, clubId)).thenReturn(null)
        whenever(membershipRepository.findActiveCountByClub(clubId)).thenReturn(10)

        assertThrows<ValidationException> { service.joinClub(userId, clubId) }
    }

    @Test
    fun `joinClub creates membership when valid`() {
        val club = makeClub(memberLimit = 20, subscriptionPrice = 150)
        val membership = makeMembership()
        whenever(clubRepository.findById(clubId)).thenReturn(club)
        whenever(membershipRepository.findByUserAndClub(userId, clubId)).thenReturn(null)
        whenever(membershipRepository.findActiveCountByClub(clubId)).thenReturn(5)
        whenever(membershipRepository.create(eq(userId), eq(clubId), any(), any(), eq(150)))
            .thenReturn(membership)

        val result = service.joinClub(userId, clubId)

        assertEquals(membership, result)
        verify(membershipRepository).create(eq(userId), eq(clubId), any(), any(), eq(150))
    }

    // isActiveMember tests

    @Test
    fun `isActiveMember returns false when no membership`() {
        whenever(membershipRepository.findByUserAndClub(userId, clubId)).thenReturn(null)

        assertFalse(service.isActiveMember(userId, clubId))
    }

    @Test
    fun `isActiveMember returns true for active status`() {
        whenever(membershipRepository.findByUserAndClub(userId, clubId))
            .thenReturn(makeMembership(status = "active"))

        assertTrue(service.isActiveMember(userId, clubId))
    }

    @Test
    fun `isActiveMember returns true for grace_period status`() {
        whenever(membershipRepository.findByUserAndClub(userId, clubId))
            .thenReturn(makeMembership(status = "grace_period"))

        assertTrue(service.isActiveMember(userId, clubId))
    }

    @Test
    fun `isActiveMember returns false for expired status`() {
        whenever(membershipRepository.findByUserAndClub(userId, clubId))
            .thenReturn(makeMembership(status = "expired"))

        assertFalse(service.isActiveMember(userId, clubId))
    }

    @Test
    fun `isActiveMember returns true for cancelled with future expiry`() {
        val futureExpiry = OffsetDateTime.now().plusDays(5)
        whenever(membershipRepository.findByUserAndClub(userId, clubId))
            .thenReturn(makeMembership(status = "cancelled", subscriptionExpiresAt = futureExpiry))

        assertTrue(service.isActiveMember(userId, clubId))
    }

    @Test
    fun `isActiveMember returns false for cancelled with past expiry`() {
        val pastExpiry = OffsetDateTime.now().minusDays(1)
        whenever(membershipRepository.findByUserAndClub(userId, clubId))
            .thenReturn(makeMembership(status = "cancelled", subscriptionExpiresAt = pastExpiry))

        assertFalse(service.isActiveMember(userId, clubId))
    }

    @Test
    fun `isActiveMember returns false for cancelled with null expiry`() {
        whenever(membershipRepository.findByUserAndClub(userId, clubId))
            .thenReturn(makeMembership(status = "cancelled", subscriptionExpiresAt = null))

        assertFalse(service.isActiveMember(userId, clubId))
    }

    // leaveClub tests

    @Test
    fun `leaveClub throws NotFoundException when no membership`() {
        whenever(membershipRepository.findByUserAndClub(userId, clubId)).thenReturn(null)

        assertThrows<NotFoundException> { service.leaveClub(userId, clubId) }
    }

    @Test
    fun `leaveClub throws ConflictException when already cancelled`() {
        whenever(membershipRepository.findByUserAndClub(userId, clubId))
            .thenReturn(makeMembership(status = "cancelled"))

        assertThrows<ConflictException> { service.leaveClub(userId, clubId) }
    }

    @Test
    fun `leaveClub updates status to cancelled`() {
        whenever(membershipRepository.findByUserAndClub(userId, clubId))
            .thenReturn(makeMembership(status = "active"))

        service.leaveClub(userId, clubId)

        verify(membershipRepository).updateStatus(userId, clubId, MembershipStatus.cancelled)
    }
}
