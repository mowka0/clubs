package com.clubs.invite

import com.clubs.club.ClubDto
import com.clubs.club.ClubRepository
import com.clubs.config.NotFoundException
import com.clubs.membership.MembershipRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class InviteLinkService(
    private val inviteLinkRepository: InviteLinkRepository,
    private val clubRepository: ClubRepository,
    private val membershipRepository: MembershipRepository
) {
    @Value("\${app.bot-username:clubsapp}")
    private lateinit var botUsername: String

    fun generateLink(clubId: UUID, userId: UUID, isSingleUse: Boolean): InviteLinkResponse {
        clubRepository.findById(clubId) ?: throw NotFoundException("Club $clubId not found")

        val membership = membershipRepository.findByUserAndClub(userId, clubId)
        if (membership == null || membership.role != "organizer") {
            throw AccessDeniedException("Only the club organizer can generate invite links")
        }

        val code = UUID.randomUUID().toString().replace("-", "")
        inviteLinkRepository.create(clubId, code, isSingleUse, userId)

        return InviteLinkResponse(
            code = code,
            link = "https://t.me/$botUsername?startapp=invite_$code",
            isSingleUse = isSingleUse
        )
    }

    fun validateAndGetClub(code: String): ClubDto {
        val link = inviteLinkRepository.findByCode(code)
            ?: throw NotFoundException("Invite link not found or already used")

        if (link.isSingleUse && link.isUsed) {
            throw NotFoundException("Invite link not found or already used")
        }

        return clubRepository.findById(link.clubId)
            ?: throw NotFoundException("Club not found")
    }

    fun consumeLink(code: String) {
        val link = inviteLinkRepository.findByCode(code)
            ?: throw NotFoundException("Invite link not found")
        if (link.isSingleUse) {
            inviteLinkRepository.markUsed(code)
        }
    }
}
