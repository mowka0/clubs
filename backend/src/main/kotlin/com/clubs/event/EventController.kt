package com.clubs.event

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.UUID

data class CreateEventRequest(
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val eventDatetime: OffsetDateTime,
    val participantLimit: Int,
    val votingOpensDaysBefore: Int = 3
)

data class UpdateEventRequest(
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val eventDatetime: OffsetDateTime? = null,
    val participantLimit: Int? = null,
    val votingOpensDaysBefore: Int? = null
)

@RestController
class EventController(
    private val eventService: EventService
) {

    @PostMapping("/api/clubs/{clubId}/events")
    fun createEvent(
        @PathVariable clubId: UUID,
        @RequestBody request: CreateEventRequest,
        authentication: Authentication
    ): ResponseEntity<EventDto> {
        val userId = UUID.fromString(authentication.principal as String)
        val event = eventService.createEvent(
            clubId = clubId,
            userId = userId,
            title = request.title,
            description = request.description,
            location = request.location,
            eventDatetime = request.eventDatetime,
            participantLimit = request.participantLimit,
            votingOpensDaysBefore = request.votingOpensDaysBefore
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(event)
    }

    @GetMapping("/api/clubs/{clubId}/events")
    fun getClubEvents(
        @PathVariable clubId: UUID,
        @RequestParam(defaultValue = "true") upcoming: Boolean,
        authentication: Authentication
    ): ResponseEntity<List<EventDto>> {
        val userId = UUID.fromString(authentication.principal as String)
        val events = eventService.getClubEvents(clubId, userId, upcoming)
        return ResponseEntity.ok(events)
    }

    @GetMapping("/api/events/{id}")
    fun getEvent(@PathVariable id: UUID): ResponseEntity<EventDto> {
        val event = eventService.getEvent(id)
        return ResponseEntity.ok(event)
    }

    @PutMapping("/api/events/{id}")
    fun updateEvent(
        @PathVariable id: UUID,
        @RequestBody request: UpdateEventRequest,
        authentication: Authentication
    ): ResponseEntity<EventDto> {
        val userId = UUID.fromString(authentication.principal as String)
        val updated = eventService.updateEvent(
            eventId = id,
            userId = userId,
            dto = UpdateEventDto(
                title = request.title,
                description = request.description,
                location = request.location,
                eventDatetime = request.eventDatetime,
                participantLimit = request.participantLimit,
                votingOpensDaysBefore = request.votingOpensDaysBefore
            )
        )
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/api/events/{id}/cancel")
    fun cancelEvent(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val userId = UUID.fromString(authentication.principal as String)
        eventService.cancelEvent(id, userId)
        return ResponseEntity.noContent().build()
    }
}
