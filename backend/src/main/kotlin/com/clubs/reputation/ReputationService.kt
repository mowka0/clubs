package com.clubs.reputation

import com.clubs.config.NotFoundException
import com.clubs.event.EventRepository
import com.clubs.event.EventResponseRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Behavior types (per PRD):
 * - Железобетонный (Iron): going -> confirmed -> attended    => +100 reliability
 * - Пустозвон (Empty):     going -> confirmed -> absent      => -50 reliability
 * - Передумавший (Backout):going -> declined (at stage 2)   => 0
 * - Спонтанный (Spontaneous): maybe -> confirmed -> attended => +30, +1 spontaneity
 * - Вечный сомневающийся:  maybe -> confirmed -> absent     => 0
 * - Молчун (Silent):       not_going or no response         => 0
 *
 * promise_fulfillment_pct = (total_attended / total_confirmed) * 100
 * Reputation is per-club.
 */
@Service
class ReputationService(
    private val eventRepository: EventRepository,
    private val eventResponseRepository: EventResponseRepository,
    private val reputationRepository: ReputationRepository
) {

    private val log = LoggerFactory.getLogger(ReputationService::class.java)

    /**
     * Recalculates and updates reputation for all confirmed participants of the given event.
     * Should be called after attendance finalization.
     */
    fun calculateAndUpdate(eventId: UUID) {
        val event = eventRepository.findById(eventId)
            ?: throw NotFoundException("Event $eventId not found")

        val responses = eventResponseRepository.findByEvent(eventId)
        var processed = 0

        for (response in responses) {
            val stage1 = response.stage1Status
            val finalStatus = response.finalStatus

            // Determine deltas for this event response
            val (reliabilityDelta, totalConfirmedDelta, totalAttendedDelta, spontaneityDelta) =
                when {
                    // Железобетонный: going -> attended
                    stage1 == "going" && finalStatus == "attended" ->
                        Deltas(100, 1, 1, 0)

                    // Пустозвон: going -> absent
                    stage1 == "going" && finalStatus == "absent" ->
                        Deltas(-50, 1, 0, 0)

                    // Передумавший: going -> declined (opted out of confirmed slot)
                    stage1 == "going" && finalStatus == "declined" ->
                        Deltas(0, 1, 0, 0)

                    // Спонтанный: maybe -> attended
                    stage1 == "maybe" && finalStatus == "attended" ->
                        Deltas(30, 1, 1, 1)

                    // Вечный сомневающийся: maybe -> absent
                    stage1 == "maybe" && finalStatus == "absent" ->
                        Deltas(0, 1, 0, 0)

                    // Молчун or waitlisted: no reputation impact
                    else -> continue
                }

            reputationRepository.upsert(
                userId = response.userId,
                clubId = event.clubId,
                reliabilityDelta = reliabilityDelta,
                totalConfirmedDelta = totalConfirmedDelta,
                totalAttendedDelta = totalAttendedDelta,
                spontaneityDelta = spontaneityDelta
            )
            processed++
        }

        log.info("Reputation updated for event $eventId (club=${event.clubId}): $processed participants processed")
    }

    fun getClubReputation(clubId: UUID): List<ReputationDto> =
        reputationRepository.findByClub(clubId)

    fun getUserClubReputation(userId: UUID, clubId: UUID): ReputationDto? =
        reputationRepository.findByUserAndClub(userId, clubId)

    fun getAllUserReputations(userId: UUID): List<ReputationDto> =
        reputationRepository.findAllByUser(userId)

    private data class Deltas(
        val reliability: Int,
        val totalConfirmed: Int,
        val totalAttended: Int,
        val spontaneity: Int
    )

    private operator fun Deltas.component1() = reliability
    private operator fun Deltas.component2() = totalConfirmed
    private operator fun Deltas.component3() = totalAttended
    private operator fun Deltas.component4() = spontaneity
}
