package com.clubs.event

import com.clubs.config.NotFoundException
import com.clubs.config.ValidationException
import com.clubs.generated.jooq.enums.EventStatus
import com.clubs.generated.jooq.enums.MembershipRole
import com.clubs.membership.MembershipRepository
import com.clubs.club.ClubRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val clubRepository: ClubRepository,
    private val membershipRepository: MembershipRepository
) {

    fun createEvent(
        clubId: UUID,
        userId: UUID,
        title: String,
        description: String?,
        location: String?,
        eventDatetime: OffsetDateTime,
        participantLimit: Int,
        votingOpensDaysBefore: Int
    ): EventDto {
        clubRepository.findById(clubId) ?: throw NotFoundException("Club $clubId not found")
        requireOrganizer(userId, clubId)
        validateEventFields(eventDatetime, participantLimit, votingOpensDaysBefore)

        return eventRepository.create(
            CreateEventDto(
                clubId = clubId,
                title = title,
                description = description,
                location = location,
                eventDatetime = eventDatetime,
                participantLimit = participantLimit,
                votingOpensDaysBefore = votingOpensDaysBefore
            )
        )
    }

    fun updateEvent(
        eventId: UUID,
        userId: UUID,
        dto: UpdateEventDto
    ): EventDto {
        val event = eventRepository.findById(eventId) ?: throw NotFoundException("Event $eventId not found")
        requireOrganizer(userId, event.clubId)

        val editableStatuses = setOf(EventStatus.upcoming.literal, EventStatus.stage_1.literal)
        if (event.status !in editableStatuses) {
            throw ValidationException("Event can only be updated in upcoming or stage_1 status")
        }

        if (dto.eventDatetime != null || dto.votingOpensDaysBefore != null) {
            val newDatetime = dto.eventDatetime ?: event.eventDatetime
            val newDaysBefore = dto.votingOpensDaysBefore ?: event.votingOpensDaysBefore
            validateEventFields(newDatetime, dto.participantLimit ?: event.participantLimit, newDaysBefore)
        }

        return eventRepository.update(eventId, dto)
    }

    fun cancelEvent(eventId: UUID, userId: UUID) {
        val event = eventRepository.findById(eventId) ?: throw NotFoundException("Event $eventId not found")
        requireOrganizer(userId, event.clubId)
        eventRepository.updateStatus(eventId, EventStatus.cancelled)
    }

    fun getEvent(eventId: UUID): EventDto {
        return eventRepository.findById(eventId) ?: throw NotFoundException("Event $eventId not found")
    }

    fun getClubEvents(clubId: UUID, userId: UUID, upcoming: Boolean): List<EventDto> {
        clubRepository.findById(clubId) ?: throw NotFoundException("Club $clubId not found")
        requireActiveMember(userId, clubId)
        return eventRepository.findByClubId(clubId, upcoming)
    }

    private fun requireOrganizer(userId: UUID, clubId: UUID) {
        val membership = membershipRepository.findByUserAndClub(userId, clubId)
            ?: throw AccessDeniedException("User is not a member of this club")
        if (membership.role != MembershipRole.organizer.literal) {
            throw AccessDeniedException("Only the club organizer can perform this action")
        }
    }

    private fun requireActiveMember(userId: UUID, clubId: UUID) {
        val membership = membershipRepository.findByUserAndClub(userId, clubId)
            ?: throw AccessDeniedException("Only club members can view events")
        val activeStatuses = setOf("active", "grace_period")
        if (membership.status !in activeStatuses) {
            throw AccessDeniedException("Only active club members can view events")
        }
    }

    private fun validateEventFields(
        eventDatetime: OffsetDateTime,
        participantLimit: Int,
        votingOpensDaysBefore: Int
    ) {
        val now = OffsetDateTime.now()
        if (!eventDatetime.isAfter(now)) {
            throw ValidationException("Event datetime must be in the future")
        }
        if (participantLimit <= 0) {
            throw ValidationException("Participant limit must be greater than 0")
        }
        if (votingOpensDaysBefore < 1 || votingOpensDaysBefore > 14) {
            throw ValidationException("Voting opens days before must be between 1 and 14")
        }
        val votingStartTime = eventDatetime.minusDays(votingOpensDaysBefore.toLong())
        if (!votingStartTime.isAfter(now)) {
            throw ValidationException(
                "Voting period (${votingOpensDaysBefore} days before event) would start in the past"
            )
        }
    }
}
