package com.clubs.reputation

import com.clubs.config.NotFoundException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ReputationController(private val reputationService: ReputationService) {

    /**
     * GET /api/clubs/{clubId}/reputation
     * List all members' reputations in a club, sorted by reliability_index DESC.
     */
    @GetMapping("/api/clubs/{clubId}/reputation")
    fun getClubReputation(
        @PathVariable clubId: UUID
    ): ResponseEntity<List<ReputationDto>> {
        return ResponseEntity.ok(reputationService.getClubReputation(clubId))
    }

    /**
     * GET /api/users/{userId}/clubs/{clubId}/reputation
     * Reputation of a specific user in a specific club.
     */
    @GetMapping("/api/users/{userId}/clubs/{clubId}/reputation")
    fun getUserClubReputation(
        @PathVariable userId: UUID,
        @PathVariable clubId: UUID
    ): ResponseEntity<ReputationDto> {
        val reputation = reputationService.getUserClubReputation(userId, clubId)
            ?: throw NotFoundException("Reputation not found for user $userId in club $clubId")
        return ResponseEntity.ok(reputation)
    }

    /**
     * GET /api/users/me/reputation
     * All reputations of the authenticated user across all clubs.
     */
    @GetMapping("/api/users/me/reputation")
    fun getMyReputations(
        authentication: Authentication
    ): ResponseEntity<List<ReputationDto>> {
        val userId = UUID.fromString(authentication.principal as String)
        return ResponseEntity.ok(reputationService.getAllUserReputations(userId))
    }
}
