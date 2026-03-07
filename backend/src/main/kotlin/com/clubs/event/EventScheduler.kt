package com.clubs.event

import com.clubs.generated.jooq.enums.EventStatus
import com.clubs.generated.jooq.enums.FinalStatus
import com.clubs.generated.jooq.tables.references.EVENTS
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class EventScheduler(
    private val dsl: DSLContext,
    private val eventRepository: EventRepository,
    private val eventResponseRepository: EventResponseRepository
) {

    private val log = LoggerFactory.getLogger(EventScheduler::class.java)

    /**
     * Every 15 minutes: transition events from upcoming -> stage_1
     * when event_datetime - voting_opens_days_before * 24h <= now()
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000L)
    fun transitionUpcomingToStage1() {
        val now = OffsetDateTime.now()

        // Find all upcoming events where voting should have started
        val eventsToTransition = dsl.selectFrom(EVENTS)
            .where(EVENTS.STATUS.eq(EventStatus.upcoming))
            .fetch()
            .filter { record ->
                val eventDatetime = record.get(EVENTS.EVENT_DATETIME)!!
                val votingDaysBefore = record.get(EVENTS.VOTING_OPENS_DAYS_BEFORE)!!
                val votingStartTime = eventDatetime.minusDays(votingDaysBefore.toLong())
                !votingStartTime.isAfter(now)
            }

        if (eventsToTransition.isEmpty()) return

        eventsToTransition.forEach { record ->
            val eventId = record.get(EVENTS.ID)!!
            eventRepository.updateStatus(eventId, EventStatus.stage_1)
        }

        log.info("Transitioned ${eventsToTransition.size} event(s) from upcoming to stage_1")
    }

    /**
     * Every 15 minutes: trigger Stage 2 for events where event_datetime - 24h <= now()
     * and stage_2_triggered = false (still in stage_1).
     *
     * Scenario A (going > limit): FIFO — first N confirmed, rest waitlisted.
     * Scenario B (going <= limit): all going confirmed, then maybe pool fills remaining spots (FIFO).
     *   If total confirmed < limit: log organizer notification needed.
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000L)
    fun triggerStage2ForEligibleEvents() {
        val events = eventRepository.findEventsReadyForStage2()
        if (events.isEmpty()) return

        events.forEach { event ->
            try {
                processStage2(event)
            } catch (e: Exception) {
                log.error("Failed to process Stage 2 for event ${event.id}: ${e.message}", e)
            }
        }

        log.info("Stage 2 processed for ${events.size} event(s)")
    }

    private fun processStage2(event: EventDto) {
        val limit = event.participantLimit
        val goingResponses = eventResponseRepository.findGoingByEvent(event.id)

        if (goingResponses.size > limit) {
            // Scenario A: excess going — FIFO assignment
            val confirmed = goingResponses.take(limit)
            val waitlisted = goingResponses.drop(limit)

            confirmed.forEach { r ->
                eventResponseRepository.updateFinalStatus(event.id, r.userId, FinalStatus.confirmed)
            }
            waitlisted.forEachIndexed { index, r ->
                eventResponseRepository.updateFinalStatus(event.id, r.userId, FinalStatus.waitlisted, waitlistPosition = index + 1)
            }

            eventRepository.markStage2Triggered(event.id, limit)
            log.info(
                "Event ${event.id} Stage 2 Scenario A: ${confirmed.size} confirmed, " +
                    "${waitlisted.size} waitlisted (limit=$limit)"
            )
        } else {
            // Scenario B: going <= limit — fill remaining spots from maybe pool (FIFO)
            val maybeResponses = eventResponseRepository.findMaybeByEvent(event.id)
            val remainingSpots = limit - goingResponses.size

            goingResponses.forEach { r ->
                eventResponseRepository.updateFinalStatus(event.id, r.userId, FinalStatus.confirmed)
            }

            val maybeToConfirm = maybeResponses.take(remainingSpots)
            maybeToConfirm.forEach { r ->
                eventResponseRepository.updateFinalStatus(event.id, r.userId, FinalStatus.confirmed)
            }

            val totalConfirmed = goingResponses.size + maybeToConfirm.size
            eventRepository.markStage2Triggered(event.id, totalConfirmed)

            if (totalConfirmed < limit) {
                log.warn(
                    "Event ${event.id} Stage 2 Scenario B: deficit — " +
                        "$totalConfirmed confirmed out of $limit. Organizer notification needed."
                )
            } else {
                log.info(
                    "Event ${event.id} Stage 2 Scenario B: $totalConfirmed confirmed " +
                        "(${goingResponses.size} going + ${maybeToConfirm.size} maybe)"
                )
            }
        }
    }
}
