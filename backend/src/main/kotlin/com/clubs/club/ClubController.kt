package com.clubs.club

import com.clubs.config.NotFoundException
import com.clubs.invite.GenerateInviteLinkRequest
import com.clubs.invite.InviteLinkResponse
import com.clubs.invite.InviteLinkService
import com.clubs.membership.MembershipRepository
import com.clubs.membership.MembershipService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class CreateClubRequest(
    val name: String,
    val description: String? = null,
    val city: String? = null,
    val category: String,
    val accessType: String,
    val memberLimit: Int,
    val subscriptionPrice: Int,
    val rules: String? = null,
    val applicationQuestion: String? = null
)

data class UpdateClubRequest(
    val name: String? = null,
    val description: String? = null,
    val city: String? = null,
    val category: String? = null,
    val accessType: String? = null,
    val memberLimit: Int? = null,
    val subscriptionPrice: Int? = null,
    val rules: String? = null,
    val applicationQuestion: String? = null
)

@RestController
@RequestMapping("/api/clubs")
class ClubController(
    private val clubService: ClubService,
    private val clubRepository: ClubRepository,
    private val membershipService: MembershipService,
    private val membershipRepository: MembershipRepository,
    private val inviteLinkService: InviteLinkService
) {

    @PostMapping
    fun createClub(
        @RequestBody request: CreateClubRequest,
        authentication: Authentication
    ): ResponseEntity<ClubDto> {
        val ownerId = UUID.fromString(authentication.principal as String)
        val club = clubService.createClub(
            ownerId = ownerId,
            name = request.name,
            description = request.description,
            city = request.city,
            category = request.category,
            accessType = request.accessType,
            memberLimit = request.memberLimit,
            subscriptionPrice = request.subscriptionPrice,
            rules = request.rules,
            applicationQuestion = request.applicationQuestion
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(club)
    }

    @GetMapping
    fun getClubs(
        @RequestParam(required = false) city: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) accessType: String?,
        @RequestParam(required = false) priceMin: Int?,
        @RequestParam(required = false) priceMax: Int?,
        @RequestParam(required = false) sizeMin: Int?,
        @RequestParam(required = false) sizeMax: Int?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "newest") sort: String
    ): ResponseEntity<PagedClubsResponse> {
        val filters = ClubFilters(
            city = city,
            category = category,
            accessType = accessType,
            priceMin = priceMin,
            priceMax = priceMax,
            memberLimitMin = sizeMin,
            memberLimitMax = sizeMax,
            search = search
        )
        val clubs = clubRepository.findAll(filters, page, size, sort)
        val total = clubRepository.countAll(filters)
        return ResponseEntity.ok(PagedClubsResponse(clubs, page, size, total))
    }

    @GetMapping("/{id}")
    fun getClub(@PathVariable id: UUID): ResponseEntity<ClubDto> {
        val club = clubRepository.findById(id) ?: throw NotFoundException("Club $id not found")
        return ResponseEntity.ok(club)
    }

    @PutMapping("/{id}")
    fun updateClub(
        @PathVariable id: UUID,
        @RequestBody request: UpdateClubRequest,
        authentication: Authentication
    ): ResponseEntity<ClubDto> {
        val userId = UUID.fromString(authentication.principal as String)
        val club = clubRepository.findById(id) ?: throw NotFoundException("Club $id not found")
        if (club.ownerId != userId) throw AccessDeniedException("Only the club owner can update it")

        val updated = clubRepository.update(
            id, UpdateClubDto(
                name = request.name,
                description = request.description,
                city = request.city,
                category = request.category,
                accessType = request.accessType,
                memberLimit = request.memberLimit,
                subscriptionPrice = request.subscriptionPrice,
                rules = request.rules,
                applicationQuestion = request.applicationQuestion
            )
        )
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{id}")
    fun deleteClub(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val userId = UUID.fromString(authentication.principal as String)
        val club = clubRepository.findById(id) ?: throw NotFoundException("Club $id not found")
        if (club.ownerId != userId) throw AccessDeniedException("Only the club owner can delete it")

        clubRepository.softDelete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/invite/{code}")
    fun getClubByInviteCode(@PathVariable code: String): ResponseEntity<ClubDto> {
        val club = inviteLinkService.validateAndGetClub(code)
        return ResponseEntity.ok(club)
    }

    @PostMapping("/{id}/invite-link")
    fun generateInviteLink(
        @PathVariable id: UUID,
        @RequestBody request: GenerateInviteLinkRequest,
        authentication: Authentication
    ): ResponseEntity<InviteLinkResponse> {
        val userId = UUID.fromString(authentication.principal as String)
        val response = inviteLinkService.generateLink(id, userId, request.isSingleUse)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{id}/members")
    fun getMembers(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<List<ClubMemberDto>> {
        val userId = UUID.fromString(authentication.principal as String)
        clubRepository.findById(id) ?: throw NotFoundException("Club $id not found")

        if (!membershipService.isActiveMember(userId, id)) {
            throw AccessDeniedException("Only club members can view the members list")
        }

        val members = membershipRepository.findMembersWithUsers(id)
        return ResponseEntity.ok(members)
    }
}
