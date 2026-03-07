package com.clubs.event

import com.clubs.generated.jooq.enums.EventStatus
import com.clubs.generated.jooq.tables.references.EVENTS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class EventRepository(private val dsl: DSLContext) {

    fun create(dto: CreateEventDto): EventDto {
        val now = OffsetDateTime.now()
        val id = UUID.randomUUID()
        dsl.insertInto(EVENTS)
            .set(EVENTS.ID, id)
            .set(EVENTS.CLUB_ID, dto.clubId)
            .set(EVENTS.TITLE, dto.title)
            .set(EVENTS.DESCRIPTION, dto.description)
            .set(EVENTS.LOCATION, dto.location)
            .set(EVENTS.EVENT_DATETIME, dto.eventDatetime)
            .set(EVENTS.PARTICIPANT_LIMIT, dto.participantLimit)
            .set(EVENTS.VOTING_OPENS_DAYS_BEFORE, dto.votingOpensDaysBefore)
            .set(EVENTS.CREATED_AT, now)
            .set(EVENTS.UPDATED_AT, now)
            .execute()
        return findById(id)!!
    }

    fun findById(id: UUID): EventDto? {
        return dsl.selectFrom(EVENTS)
            .where(EVENTS.ID.eq(id))
            .fetchOne()
            ?.toDto()
    }

    fun findByClubId(clubId: UUID, upcoming: Boolean): List<EventDto> {
        val now = OffsetDateTime.now()
        val query = dsl.selectFrom(EVENTS)
            .where(EVENTS.CLUB_ID.eq(clubId))
        return if (upcoming) {
            query.and(EVENTS.EVENT_DATETIME.gt(now))
                .orderBy(EVENTS.EVENT_DATETIME.asc())
                .fetch()
                .map { it.toDto() }
        } else {
            query.and(EVENTS.EVENT_DATETIME.le(now))
                .orderBy(EVENTS.EVENT_DATETIME.desc())
                .fetch()
                .map { it.toDto() }
        }
    }

    fun findUpcomingByClub(clubId: UUID): List<EventDto> = findByClubId(clubId, upcoming = true)

    fun updateStatus(id: UUID, status: EventStatus) {
        dsl.update(EVENTS)
            .set(EVENTS.STATUS, status)
            .set(EVENTS.UPDATED_AT, OffsetDateTime.now())
            .where(EVENTS.ID.eq(id))
            .execute()
    }

    fun update(id: UUID, dto: UpdateEventDto): EventDto {
        val now = OffsetDateTime.now()
        val update = dsl.update(EVENTS).set(EVENTS.UPDATED_AT, now)
        dto.title?.let { update.set(EVENTS.TITLE, it) }
        dto.description?.let { update.set(EVENTS.DESCRIPTION, it) }
        dto.location?.let { update.set(EVENTS.LOCATION, it) }
        dto.eventDatetime?.let { update.set(EVENTS.EVENT_DATETIME, it) }
        dto.participantLimit?.let { update.set(EVENTS.PARTICIPANT_LIMIT, it) }
        dto.votingOpensDaysBefore?.let { update.set(EVENTS.VOTING_OPENS_DAYS_BEFORE, it) }
        update.where(EVENTS.ID.eq(id)).execute()
        return findById(id) ?: throw IllegalStateException("Event $id not found after update")
    }

    /**
     * Returns all stage_1 events where event_datetime - 24h <= now() and stage_2 not yet triggered.
     */
    fun findEventsReadyForStage2(): List<EventDto> {
        val cutoff = OffsetDateTime.now().plusHours(24)
        return dsl.selectFrom(EVENTS)
            .where(EVENTS.STATUS.eq(EventStatus.stage_1))
            .and(EVENTS.STAGE_2_TRIGGERED.eq(false))
            .and(EVENTS.EVENT_DATETIME.le(cutoff))
            .fetch()
            .map { it.toDto() }
    }

    /**
     * Marks the event as stage_2 triggered: sets stage_2_triggered=true, status=stage_2, confirmed_count.
     */
    fun markStage2Triggered(id: UUID, confirmedCount: Int) {
        dsl.update(EVENTS)
            .set(EVENTS.STAGE_2_TRIGGERED, true)
            .set(EVENTS.STATUS, EventStatus.stage_2)
            .set(EVENTS.CONFIRMED_COUNT, confirmedCount)
            .set(EVENTS.UPDATED_AT, OffsetDateTime.now())
            .where(EVENTS.ID.eq(id))
            .execute()
    }

    fun decrementConfirmedCount(id: UUID) {
        dsl.update(EVENTS)
            .set(EVENTS.CONFIRMED_COUNT, EVENTS.CONFIRMED_COUNT.minus(1))
            .set(EVENTS.UPDATED_AT, OffsetDateTime.now())
            .where(EVENTS.ID.eq(id))
            .and(EVENTS.CONFIRMED_COUNT.gt(0))
            .execute()
    }

    /**
     * Atomically increments confirmed_count if below limit. Returns new count, or null if limit reached.
     */
    fun atomicIncrementConfirmedCount(id: UUID, limit: Int): Int? {
        val result = dsl.resultQuery(
            "UPDATE events SET confirmed_count = confirmed_count + 1, updated_at = now() " +
                "WHERE id = ? AND confirmed_count < ? RETURNING confirmed_count",
            id, limit
        ).fetch()
        return if (result.isEmpty()) null else result.first().get(0, Int::class.java)
    }

    private fun org.jooq.Record.toDto(): EventDto = EventDto(
        id = get(EVENTS.ID)!!,
        clubId = get(EVENTS.CLUB_ID)!!,
        title = get(EVENTS.TITLE)!!,
        description = get(EVENTS.DESCRIPTION),
        location = get(EVENTS.LOCATION),
        eventDatetime = get(EVENTS.EVENT_DATETIME)!!,
        participantLimit = get(EVENTS.PARTICIPANT_LIMIT)!!,
        confirmedCount = get(EVENTS.CONFIRMED_COUNT)!!,
        votingOpensDaysBefore = get(EVENTS.VOTING_OPENS_DAYS_BEFORE)!!,
        status = get(EVENTS.STATUS)!!.literal,
        stage2Triggered = get(EVENTS.STAGE_2_TRIGGERED)!!,
        attendanceFinalized = get(EVENTS.ATTENDANCE_FINALIZED)!!,
        attendanceFinalizedAt = get(EVENTS.ATTENDANCE_FINALIZED_AT),
        createdAt = get(EVENTS.CREATED_AT)!!,
        updatedAt = get(EVENTS.UPDATED_AT)!!
    )
}
