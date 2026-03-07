package com.clubs.event

import com.clubs.config.NotFoundException
import org.springframework.security.access.AccessDeniedException
import com.clubs.config.ValidationException
import com.clubs.generated.jooq.enums.EventStatus
import com.clubs.generated.jooq.enums.VoteStatus
import com.clubs.membership.MembershipService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class EventResponseService(
    private val eventResponseRepository: EventResponseRepository,
    private val eventRepository: EventRepository,
    private val membershipService: MembershipService
) {

    fun vote(userId: UUID, eventId: UUID, status: String): EventResponseDto {
        val event = eventRepository.findById(eventId)
            ?: throw NotFoundException("Event $eventId not found")

        if (event.status != EventStatus.stage_1.literal) {
            throw ValidationException("Voting is only allowed during stage_1. Current status: ${event.status}")
        }

        if (!membershipService.isActiveMember(userId, event.clubId)) {
            throw AccessDeniedException("Only active club members can vote")
        }

        val voteStatus = VoteStatus.entries.find { it.literal == status }
            ?: throw ValidationException("Invalid vote status: $status. Must be one of: going, maybe, not_going")

        return eventResponseRepository.createOrUpdate(eventId, userId, voteStatus)
    }

    fun countByStatus(eventId: UUID): VoteCountsDto {
        eventRepository.findById(eventId) ?: throw NotFoundException("Event $eventId not found")
        return eventResponseRepository.countByStatus(eventId)
    }

    fun getStats(eventId: UUID): EventStatsDto {
        val event = eventRepository.findById(eventId)
            ?: throw NotFoundException("Event $eventId not found")
        val counts = eventResponseRepository.countByStatus(eventId)
        return EventStatsDto(
            going = counts.going,
            maybe = counts.maybe,
            notGoing = counts.notGoing,
            confirmed = event.confirmedCount,
            limit = event.participantLimit
        )
    }

    fun getResponses(eventId: UUID, requesterId: UUID): List<EventResponseDto> {
        val event = eventRepository.findById(eventId)
            ?: throw NotFoundException("Event $eventId not found")

        if (!membershipService.isActiveMember(requesterId, event.clubId)) {
            throw AccessDeniedException("Only active club members can view responses")
        }

        return eventResponseRepository.findByEvent(eventId)
    }
}
