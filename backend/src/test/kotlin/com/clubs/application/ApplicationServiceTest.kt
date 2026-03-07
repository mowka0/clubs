package com.clubs.application

import com.clubs.club.ClubRepository
import com.clubs.config.ConflictException
import com.clubs.config.NotFoundException
import com.clubs.config.ValidationException
import com.clubs.generated.jooq.enums.ApplicationStatus
import com.clubs.membership.MembershipDto
import com.clubs.membership.MembershipRepository
import com.clubs.membership.MembershipService
import com.clubs.notification.NotificationService
import com.clubs.user.UserService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.access.AccessDeniedException
import java.time.OffsetDateTime
import java.util.UUID

class ApplicationServiceTest {

    private lateinit var applicationRepository: ApplicationRepository
    private lateinit var clubRepository: ClubRepository
    private lateinit var membershipRepository: MembershipRepository
    private lateinit var membershipService: MembershipService
    private lateinit var userService: UserService
    private lateinit var notificationService: NotificationService
    private lateinit var service: ApplicationService

    private val userId = UUID.randomUUID()
    private val organizerId = UUID.randomUUID()
    private val clubId = UUID.randomUUID()
    private val applicationId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        applicationRepository = mock()
        clubRepository = mock()
        membershipRepository = mock()
        membershipService = mock()
        userService = mock()
        notificationService = mock()
        // Default: findById returns null so notifications are silently skipped
        whenever(userService.findById(any())).thenReturn(null)
        service = ApplicationService(applicationRepository, clubRepository, membershipRepository, membershipService, userService, notificationService)
    }

    private fun makeApplication(
        id: UUID = applicationId,
        status: String = "pending"
    ): ApplicationDto {
        val now = OffsetDateTime.now()
        return ApplicationDto(
            id = id,
            userId = userId,
            clubId = clubId,
            answerText = "I love sports",
            status = status,
            rejectionReason = null,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun makeOrganizerMembership(): com.clubs.membership.MembershipDto {
        val now = OffsetDateTime.now()
        return com.clubs.membership.MembershipDto(
            id = UUID.randomUUID(),
            userId = organizerId,
            clubId = clubId,
            role = "organizer",
            status = "active",
            joinedAt = now,
            subscriptionExpiresAt = null,
            lockedSubscriptionPrice = null,
            createdAt = now,
            updatedAt = now
        )
    }

    // submitApplication tests

    @Test
    fun `submitApplication throws NotFoundException when club does not exist`() {
        whenever(clubRepository.findById(clubId)).thenReturn(null)

        assertThrows<NotFoundException> { service.submitApplication(userId, clubId, "answer") }
    }

    @Test
    fun `submitApplication throws ConflictException when already a member`() {
        val now = OffsetDateTime.now()
        whenever(clubRepository.findById(clubId)).thenReturn(makeClubDto())
        whenever(membershipRepository.findByUserAndClub(userId, clubId)).thenReturn(
            com.clubs.membership.MembershipDto(
                id = UUID.randomUUID(), userId = userId, clubId = clubId,
                role = "member", status = "active", joinedAt = now,
                subscriptionExpiresAt = null, lockedSubscriptionPrice = null,
                createdAt = now, updatedAt = now
            )
        )

        assertThrows<ConflictException> { service.submitApplication(userId, clubId, "answer") }
    }

    @Test
    fun `submitApplication throws ConflictException when pending application exists`() {
        whenever(clubRepository.findById(clubId)).thenReturn(makeClubDto())
        whenever(membershipRepository.findByUserAndClub(userId, clubId)).thenReturn(null)
        whenever(applicationRepository.findPendingByUserAndClub(userId, clubId)).thenReturn(makeApplication())

        assertThrows<ConflictException> { service.submitApplication(userId, clubId, "answer") }
    }

    @Test
    fun `submitApplication creates application when valid`() {
        val application = makeApplication()
        whenever(clubRepository.findById(clubId)).thenReturn(makeClubDto())
        whenever(membershipRepository.findByUserAndClub(userId, clubId)).thenReturn(null)
        whenever(applicationRepository.findPendingByUserAndClub(userId, clubId)).thenReturn(null)
        whenever(applicationRepository.create(userId, clubId, "answer")).thenReturn(application)

        val result = service.submitApplication(userId, clubId, "answer")

        assertEquals(application, result)
        verify(applicationRepository).create(userId, clubId, "answer")
    }

    // approveApplication tests

    @Test
    fun `approveApplication throws NotFoundException when application not found`() {
        whenever(applicationRepository.findById(applicationId)).thenReturn(null)

        assertThrows<NotFoundException> { service.approveApplication(applicationId, organizerId) }
    }

    @Test
    fun `approveApplication throws AccessDeniedException when not organizer`() {
        val nonOrganizerMembership = com.clubs.membership.MembershipDto(
            id = UUID.randomUUID(), userId = organizerId, clubId = clubId,
            role = "member", status = "active", joinedAt = OffsetDateTime.now(),
            subscriptionExpiresAt = null, lockedSubscriptionPrice = null,
            createdAt = OffsetDateTime.now(), updatedAt = OffsetDateTime.now()
        )
        whenever(applicationRepository.findById(applicationId)).thenReturn(makeApplication())
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(nonOrganizerMembership)

        assertThrows<AccessDeniedException> { service.approveApplication(applicationId, organizerId) }
    }

    @Test
    fun `approveApplication throws ValidationException when not pending`() {
        whenever(applicationRepository.findById(applicationId)).thenReturn(makeApplication(status = "approved"))
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())

        assertThrows<ValidationException> { service.approveApplication(applicationId, organizerId) }
    }

    @Test
    fun `approveApplication creates membership and sets status approved`() {
        val pendingApp = makeApplication(status = "pending")
        val approvedApp = makeApplication(status = "approved")
        val membership = com.clubs.membership.MembershipDto(
            id = UUID.randomUUID(), userId = userId, clubId = clubId,
            role = "member", status = "active", joinedAt = OffsetDateTime.now(),
            subscriptionExpiresAt = null, lockedSubscriptionPrice = null,
            createdAt = OffsetDateTime.now(), updatedAt = OffsetDateTime.now()
        )
        whenever(applicationRepository.findById(applicationId))
            .thenReturn(pendingApp)
            .thenReturn(approvedApp)
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())
        whenever(membershipService.joinClub(userId, clubId)).thenReturn(membership)

        val result = service.approveApplication(applicationId, organizerId)

        verify(membershipService).joinClub(userId, clubId)
        verify(applicationRepository).updateStatus(applicationId, ApplicationStatus.approved)
        assertEquals("approved", result.status)
    }

    // rejectApplication tests

    @Test
    fun `rejectApplication throws NotFoundException when application not found`() {
        whenever(applicationRepository.findById(applicationId)).thenReturn(null)

        assertThrows<NotFoundException> { service.rejectApplication(applicationId, organizerId, null) }
    }

    @Test
    fun `rejectApplication throws AccessDeniedException when not organizer`() {
        val nonOrganizerMembership = com.clubs.membership.MembershipDto(
            id = UUID.randomUUID(), userId = organizerId, clubId = clubId,
            role = "member", status = "active", joinedAt = OffsetDateTime.now(),
            subscriptionExpiresAt = null, lockedSubscriptionPrice = null,
            createdAt = OffsetDateTime.now(), updatedAt = OffsetDateTime.now()
        )
        whenever(applicationRepository.findById(applicationId)).thenReturn(makeApplication())
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(nonOrganizerMembership)

        assertThrows<AccessDeniedException> { service.rejectApplication(applicationId, organizerId, null) }
    }

    @Test
    fun `rejectApplication sets status rejected with reason`() {
        val pendingApp = makeApplication(status = "pending")
        val rejectedApp = makeApplication(status = "rejected")
        whenever(applicationRepository.findById(applicationId))
            .thenReturn(pendingApp)
            .thenReturn(rejectedApp)
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())

        val result = service.rejectApplication(applicationId, organizerId, "Not a good fit")

        verify(applicationRepository).updateStatus(applicationId, ApplicationStatus.rejected, "Not a good fit")
        assertEquals("rejected", result.status)
    }

    private fun makeClubDto(): com.clubs.club.ClubDto {
        val now = OffsetDateTime.now()
        return com.clubs.club.ClubDto(
            id = clubId,
            ownerId = organizerId,
            name = "Test Club",
            description = null,
            city = null,
            category = "sport",
            accessType = "closed",
            memberLimit = 20,
            subscriptionPrice = 100,
            avatarUrl = null,
            coverUrl = null,
            rules = null,
            applicationQuestion = "Why do you want to join?",
            telegramGroupId = null,
            activityRating = 0.0,
            confirmedCount = 0,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
    }
}
