package com.clubs.event

import com.clubs.config.NotFoundException
import org.springframework.security.access.AccessDeniedException
import com.clubs.config.ValidationException
import com.clubs.generated.jooq.enums.EventStatus
import com.clubs.generated.jooq.enums.FinalStatus
import com.clubs.generated.jooq.enums.VoteStatus
import com.clubs.membership.MembershipService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class EventResponseService(
    private val eventResponseRepository: EventResponseRepository,
    private val eventRepository: EventRepository,
    private val membershipService: MembershipService
) {

    private val log = LoggerFactory.getLogger(EventResponseService::class.java)

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

    fun confirm(userId: UUID, eventId: UUID): ConfirmDeclineResponse {
        val event = eventRepository.findById(eventId)
            ?: throw NotFoundException("Event $eventId not found")

        if (event.status != EventStatus.stage_2.literal) {
            throw ValidationException("Confirm is only available during stage_2. Current status: ${event.status}")
        }

        val response = eventResponseRepository.findByEventAndUser(eventId, userId)
            ?: throw NotFoundException("No vote found for user in event $eventId")

        if (response.stage1Status != VoteStatus.going.literal && response.stage1Status != VoteStatus.maybe.literal) {
            throw ValidationException("Only users who voted 'going' or 'maybe' can confirm")
        }

        // Idempotent: already confirmed
        if (response.finalStatus == FinalStatus.confirmed.literal) {
            return ConfirmDeclineResponse(finalStatus = "confirmed", positionInWaitlist = null)
        }

        // Try to get a slot atomically
        val newCount = eventRepository.atomicIncrementConfirmedCount(eventId, event.participantLimit)

        return if (newCount != null) {
            eventResponseRepository.updateFinalStatus(eventId, userId, FinalStatus.confirmed)
            ConfirmDeclineResponse(finalStatus = "confirmed", positionInWaitlist = null)
        } else {
            val maxPosition = eventResponseRepository.findWaitlistedByEvent(eventId)
                .maxOfOrNull { it.waitlistPosition ?: 0 } ?: 0
            val position = maxPosition + 1
            eventResponseRepository.updateFinalStatus(eventId, userId, FinalStatus.waitlisted, waitlistPosition = position)
            ConfirmDeclineResponse(finalStatus = "waitlisted", positionInWaitlist = position)
        }
    }

    fun decline(userId: UUID, eventId: UUID): ConfirmDeclineResponse {
        val event = eventRepository.findById(eventId)
            ?: throw NotFoundException("Event $eventId not found")

        if (event.status != EventStatus.stage_2.literal) {
            throw ValidationException("Decline is only available during stage_2. Current status: ${event.status}")
        }

        val response = eventResponseRepository.findByEventAndUser(eventId, userId)
            ?: throw NotFoundException("No vote found for user in event $eventId")

        val wasConfirmed = response.finalStatus == FinalStatus.confirmed.literal

        eventResponseRepository.updateFinalStatus(eventId, userId, FinalStatus.declined)

        if (wasConfirmed) {
            eventRepository.decrementConfirmedCount(eventId)
            val firstWaitlisted = eventResponseRepository.findWaitlistedByEvent(eventId).firstOrNull()
            if (firstWaitlisted != null) {
                val newCount = eventRepository.atomicIncrementConfirmedCount(eventId, event.participantLimit)
                if (newCount != null) {
                    eventResponseRepository.updateFinalStatus(eventId, firstWaitlisted.userId, FinalStatus.confirmed)
                    log.info("Promoted user ${firstWaitlisted.userId} from waitlist to confirmed for event $eventId")
                }
            }
        }

        return ConfirmDeclineResponse(finalStatus = "declined", positionInWaitlist = null)
    }
}
