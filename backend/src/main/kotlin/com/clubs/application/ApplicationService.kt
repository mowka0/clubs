package com.clubs.application

import com.clubs.club.ClubRepository
import com.clubs.config.ConflictException
import com.clubs.config.NotFoundException
import com.clubs.config.ValidationException
import com.clubs.generated.jooq.enums.ApplicationStatus
import com.clubs.generated.jooq.enums.MembershipRole
import com.clubs.membership.MembershipRepository
import com.clubs.membership.MembershipService
import com.clubs.notification.NotificationService
import com.clubs.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ApplicationService(
    private val applicationRepository: ApplicationRepository,
    private val clubRepository: ClubRepository,
    private val membershipRepository: MembershipRepository,
    private val membershipService: MembershipService,
    private val userService: UserService,
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun submitApplication(userId: UUID, clubId: UUID, answerText: String?): ApplicationDto {
        clubRepository.findById(clubId)
            ?: throw NotFoundException("Club $clubId not found")

        val existing = membershipRepository.findByUserAndClub(userId, clubId)
        if (existing != null) {
            throw ConflictException("Already a member of this club")
        }

        val pendingApp = applicationRepository.findPendingByUserAndClub(userId, clubId)
        if (pendingApp != null) {
            throw ConflictException("Application already exists")
        }

        return applicationRepository.create(userId, clubId, answerText)
    }

    fun approveApplication(applicationId: UUID, organizerId: UUID): ApplicationDto {
        val application = applicationRepository.findById(applicationId)
            ?: throw NotFoundException("Application $applicationId not found")

        requireOrganizer(organizerId, application.clubId)

        if (application.status != ApplicationStatus.pending.literal) {
            throw ValidationException("Application is not in pending status")
        }

        membershipService.joinClub(application.userId, application.clubId)

        applicationRepository.updateStatus(applicationId, ApplicationStatus.approved)

        // Notify applicant
        try {
            val club = clubRepository.findById(application.clubId)
            val user = userService.findById(application.userId)
            if (club != null && user != null) {
                notificationService.notifyApplicationApproved(user.telegramId, club.name, club.id)
            }
        } catch (e: Exception) {
            log.warn("Failed to send application approved notification for applicationId={}", applicationId, e)
        }

        return applicationRepository.findById(applicationId)!!
    }

    fun rejectApplication(applicationId: UUID, organizerId: UUID, reason: String?): ApplicationDto {
        val application = applicationRepository.findById(applicationId)
            ?: throw NotFoundException("Application $applicationId not found")

        requireOrganizer(organizerId, application.clubId)

        if (application.status != ApplicationStatus.pending.literal) {
            throw ValidationException("Application is not in pending status")
        }

        applicationRepository.updateStatus(applicationId, ApplicationStatus.rejected, reason)

        // Notify applicant
        try {
            val club = clubRepository.findById(application.clubId)
            val user = userService.findById(application.userId)
            if (club != null && user != null) {
                notificationService.notifyApplicationRejected(user.telegramId, club.name, reason)
            }
        } catch (e: Exception) {
            log.warn("Failed to send application rejected notification for applicationId={}", applicationId, e)
        }

        return applicationRepository.findById(applicationId)!!
    }

    fun getClubApplications(clubId: UUID, requesterId: UUID, status: ApplicationStatus? = null): List<ApplicationDto> {
        requireOrganizer(requesterId, clubId)
        return applicationRepository.findByClubId(clubId, status)
    }

    fun getMyApplications(userId: UUID): List<ApplicationDto> {
        return applicationRepository.findAllByUser(userId)
    }

    private fun requireOrganizer(userId: UUID, clubId: UUID) {
        val membership = membershipRepository.findByUserAndClub(userId, clubId)
            ?: throw NotFoundException("Membership not found")
        if (membership.role != MembershipRole.organizer.literal) {
            throw org.springframework.security.access.AccessDeniedException("Only organizer can perform this action")
        }
    }
}
