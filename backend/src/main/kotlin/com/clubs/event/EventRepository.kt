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
     * Returns attendance_first_recorded_at for the given event, or null if not yet set.
     */
    fun getAttendanceFirstRecordedAt(id: UUID): java.time.OffsetDateTime? {
        val result = dsl.resultQuery(
            "SELECT attendance_first_recorded_at FROM events WHERE id = ?", id
        ).fetch()
        if (result.isEmpty()) return null
        return result.first().get(0, java.time.OffsetDateTime::class.java)
    }

    /**
     * Sets attendance_first_recorded_at = now() only if not already set.
     */
    fun markAttendanceFirstRecorded(id: UUID) {
        dsl.execute(
            "UPDATE events SET attendance_first_recorded_at = now(), updated_at = now() " +
                "WHERE id = ? AND attendance_first_recorded_at IS NULL",
            id
        )
    }

    /**
     * Finalizes attendance: sets attendance_finalized=true and attendance_finalized_at=now().
     */
    fun finalizeAttendance(id: UUID) {
        dsl.update(EVENTS)
            .set(EVENTS.ATTENDANCE_FINALIZED, true)
            .set(EVENTS.ATTENDANCE_FINALIZED_AT, OffsetDateTime.now())
            .set(EVENTS.UPDATED_AT, OffsetDateTime.now())
            .where(EVENTS.ID.eq(id))
            .execute()
    }

    /**
     * Finds events where attendance was first recorded 48+ hours ago and not yet finalized.
     */
    fun findEventsReadyForFinalization(): List<EventDto> {
        return dsl.resultQuery(
            "SELECT * FROM events WHERE attendance_first_recorded_at IS NOT NULL " +
                "AND attendance_finalized = false " +
                "AND attendance_first_recorded_at + INTERVAL '48 hours' <= now()"
        ).fetch()
            .map { record ->
                EventDto(
                    id = record.get("id", UUID::class.java)!!,
                    clubId = record.get("club_id", UUID::class.java)!!,
                    title = record.get("title", String::class.java)!!,
                    description = record.get("description", String::class.java),
                    location = record.get("location", String::class.java),
                    eventDatetime = record.get("event_datetime", OffsetDateTime::class.java)!!,
                    participantLimit = record.get("participant_limit", Int::class.java)!!,
                    confirmedCount = record.get("confirmed_count", Int::class.java)!!,
                    votingOpensDaysBefore = record.get("voting_opens_days_before", Int::class.java)!!,
                    status = record.get("status", String::class.java)!!,
                    stage2Triggered = record.get("stage_2_triggered", Boolean::class.java)!!,
                    attendanceFinalized = record.get("attendance_finalized", Boolean::class.java)!!,
                    attendanceFinalizedAt = record.get("attendance_finalized_at", OffsetDateTime::class.java),
                    createdAt = record.get("created_at", OffsetDateTime::class.java)!!,
                    updatedAt = record.get("updated_at", OffsetDateTime::class.java)!!
                )
            }
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
