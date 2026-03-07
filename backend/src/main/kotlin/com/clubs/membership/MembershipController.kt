package com.clubs.membership

import com.clubs.bot.TelegramApiClient
import com.clubs.club.ClubRepository
import com.clubs.config.NotFoundException
import com.clubs.config.ValidationException
import com.clubs.invite.InviteLinkService
import com.clubs.notification.NotificationService
import com.clubs.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class JoinClubResponse(
    val membership: MembershipDto,
    val telegramInviteLink: String?
)

@RestController
@RequestMapping("/api/clubs")
class MembershipController(
    private val membershipService: MembershipService,
    private val clubRepository: ClubRepository,
    private val inviteLinkService: InviteLinkService,
    private val telegramApiClient: TelegramApiClient,
    private val notificationService: NotificationService,
    private val userService: UserService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{id}/join")
    fun joinClub(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<JoinClubResponse> {
        val userId = UUID.fromString(authentication.principal as String)

        val club = clubRepository.findById(id) ?: throw NotFoundException("Club $id not found")

        if (club.accessType != "open") {
            throw ValidationException("This club is not open for direct joining. Use /api/clubs/$id/apply instead")
        }

        val membership = membershipService.joinClub(userId, id)

        val telegramInviteLink = club.telegramGroupId?.let {
            telegramApiClient.createChatInviteLink(it)
        }

        // Welcome notification to group
        club.telegramGroupId?.let { chatId ->
            try {
                val user = userService.findById(userId)
                val firstName = user?.firstName ?: user?.username ?: "Новый участник"
                notificationService.notifyGroupNewMember(chatId, firstName, club.id)
            } catch (e: Exception) {
                log.warn("Failed to send welcome notification for userId=$userId club=${club.id}: ${e.message}")
            }
        }

        return ResponseEntity.ok(JoinClubResponse(membership, telegramInviteLink))
    }

    @PostMapping("/invite/{code}/join")
    fun joinByInvite(
        @PathVariable code: String,
        authentication: Authentication
    ): ResponseEntity<JoinClubResponse> {
        val userId = UUID.fromString(authentication.principal as String)

        val club = inviteLinkService.validateAndGetClub(code)

        val membership = membershipService.joinClub(userId, club.id)

        inviteLinkService.consumeLink(code)

        val telegramInviteLink = club.telegramGroupId?.let {
            telegramApiClient.createChatInviteLink(it)
        }

        // Welcome notification to group
        club.telegramGroupId?.let { chatId ->
            try {
                val user = userService.findById(userId)
                val firstName = user?.firstName ?: user?.username ?: "Новый участник"
                notificationService.notifyGroupNewMember(chatId, firstName, club.id)
            } catch (e: Exception) {
                log.warn("Failed to send welcome notification for userId=$userId club=${club.id}: ${e.message}")
            }
        }

        return ResponseEntity.ok(JoinClubResponse(membership, telegramInviteLink))
    }

    @PostMapping("/{id}/leave")
    fun leaveClub(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<MembershipDto> {
        val userId = UUID.fromString(authentication.principal as String)

        clubRepository.findById(id) ?: throw NotFoundException("Club $id not found")

        membershipService.leaveClub(userId, id)

        val membership = membershipService.getMembership(userId, id)
            ?: throw NotFoundException("Membership not found")

        return ResponseEntity.ok(membership)
    }
}
