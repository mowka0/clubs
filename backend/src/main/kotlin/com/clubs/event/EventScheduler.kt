package com.clubs.event

import com.clubs.generated.jooq.enums.EventStatus
import com.clubs.generated.jooq.tables.references.EVENTS
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class EventScheduler(
    private val dsl: DSLContext,
    private val eventRepository: EventRepository
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
}
