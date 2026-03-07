package com.clubs.event

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class VoteRequest(val status: String)

@RestController
class EventResponseController(
    private val eventResponseService: EventResponseService
) {

    @PostMapping("/api/events/{id}/vote")
    fun vote(
        @PathVariable id: UUID,
        @RequestBody request: VoteRequest,
        authentication: Authentication
    ): ResponseEntity<EventResponseDto> {
        val userId = UUID.fromString(authentication.principal as String)
        val response = eventResponseService.vote(userId, id, request.status)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/api/events/{id}/stats")
    fun getStats(@PathVariable id: UUID): ResponseEntity<EventStatsDto> {
        val stats = eventResponseService.getStats(id)
        return ResponseEntity.ok(stats)
    }

    @GetMapping("/api/events/{id}/responses")
    fun getResponses(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<List<EventResponseDto>> {
        val userId = UUID.fromString(authentication.principal as String)
        val responses = eventResponseService.getResponses(id, userId)
        return ResponseEntity.ok(responses)
    }
}
