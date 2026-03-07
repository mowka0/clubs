package com.clubs.event

import com.clubs.generated.jooq.enums.EventStatus
import com.clubs.generated.jooq.enums.FinalStatus
import com.clubs.generated.jooq.tables.references.EVENTS
import com.clubs.notification.NotificationService
import com.clubs.reputation.ReputationService
import com.clubs.user.UserService
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class EventScheduler(
    private val dsl: DSLContext,
    private val eventRepository: EventRepository,
    private val eventResponseRepository: EventResponseRepository,
    private val reputationService: ReputationService,
    private val userService: UserService,
    private val notificationService: NotificationService
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

    /**
     * Every hour: finalize attendance for events where attendance_first_recorded_at + 48h <= now().
     * After finalization, attendance records are immutable.
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    fun finalizeAttendanceForEligibleEvents() {
        val events = eventRepository.findEventsReadyForFinalization()
        if (events.isEmpty()) return

        events.forEach { event ->
            try {
                eventRepository.finalizeAttendance(event.id)
                log.info("Finalized attendance for event ${event.id}")
                reputationService.calculateAndUpdate(event.id)
            } catch (e: Exception) {
                log.error("Failed to finalize attendance for event ${event.id}: ${e.message}", e)
            }
        }

        log.info("Attendance finalized for ${events.size} event(s)")
    }

    /**
     * Every hour: notify organizers that 12h have passed since their event (attendance marking reminder).
     * Finds events where event_datetime + 12h <= now() and attendance_finalized = false.
     * Logs a reminder (actual push notification requires TASK-032).
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    fun remindOrganizersToMarkAttendance() {
        val cutoff = OffsetDateTime.now().minusHours(12)
        val events = dsl.selectFrom(EVENTS)
            .where(EVENTS.EVENT_DATETIME.le(cutoff))
            .and(EVENTS.ATTENDANCE_FINALIZED.eq(false))
            .and(EVENTS.STATUS.eq(EventStatus.stage_2))
            .fetch()

        events.forEach { record ->
            log.info(
                "Organizer notification needed: event ${record.get(EVENTS.ID)} is ready for attendance marking"
            )
        }
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

            // Notify participants
            confirmed.forEach { r ->
                userService.findById(r.userId)?.let { user ->
                    notificationService.notifyStage2Started(user.telegramId, event.title, event.id)
                }
            }
            waitlisted.forEachIndexed { index, r ->
                userService.findById(r.userId)?.let { user ->
                    notificationService.notifyWaitlisted(user.telegramId, event.title, event.id, index + 1)
                }
            }
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

            // Notify all confirmed participants
            val allConfirmed = goingResponses + maybeToConfirm
            allConfirmed.forEach { r ->
                userService.findById(r.userId)?.let { user ->
                    notificationService.notifyStage2Started(user.telegramId, event.title, event.id)
                }
            }
        }
    }
}
