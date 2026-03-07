package com.clubs.event

import com.clubs.generated.jooq.enums.VoteStatus
import com.clubs.generated.jooq.tables.references.EVENT_RESPONSES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class EventResponseRepository(private val dsl: DSLContext) {

    fun createOrUpdate(eventId: UUID, userId: UUID, stage1Status: VoteStatus): EventResponseDto {
        val now = OffsetDateTime.now()
        val existing = findByEventAndUser(eventId, userId)

        if (existing == null) {
            val id = UUID.randomUUID()
            dsl.insertInto(EVENT_RESPONSES)
                .set(EVENT_RESPONSES.ID, id)
                .set(EVENT_RESPONSES.EVENT_ID, eventId)
                .set(EVENT_RESPONSES.USER_ID, userId)
                .set(EVENT_RESPONSES.STAGE_1_STATUS, stage1Status)
                .set(EVENT_RESPONSES.RESPONDED_AT, now)
                .set(EVENT_RESPONSES.CREATED_AT, now)
                .set(EVENT_RESPONSES.UPDATED_AT, now)
                .execute()
        } else {
            dsl.update(EVENT_RESPONSES)
                .set(EVENT_RESPONSES.STAGE_1_STATUS, stage1Status)
                .set(EVENT_RESPONSES.RESPONDED_AT, now)
                .set(EVENT_RESPONSES.UPDATED_AT, now)
                .where(EVENT_RESPONSES.EVENT_ID.eq(eventId))
                .and(EVENT_RESPONSES.USER_ID.eq(userId))
                .execute()
        }

        return findByEventAndUser(eventId, userId)!!
    }

    fun findByEventAndUser(eventId: UUID, userId: UUID): EventResponseDto? {
        return dsl.selectFrom(EVENT_RESPONSES)
            .where(EVENT_RESPONSES.EVENT_ID.eq(eventId))
            .and(EVENT_RESPONSES.USER_ID.eq(userId))
            .fetchOne()
            ?.toDto()
    }

    fun findByEvent(eventId: UUID): List<EventResponseDto> {
        return dsl.selectFrom(EVENT_RESPONSES)
            .where(EVENT_RESPONSES.EVENT_ID.eq(eventId))
            .orderBy(EVENT_RESPONSES.RESPONDED_AT.asc())
            .fetch()
            .map { it.toDto() }
    }

    fun countByStatus(eventId: UUID): VoteCountsDto {
        val responses = findByEvent(eventId)
        return VoteCountsDto(
            going = responses.count { it.stage1Status == VoteStatus.going.literal },
            maybe = responses.count { it.stage1Status == VoteStatus.maybe.literal },
            notGoing = responses.count { it.stage1Status == VoteStatus.not_going.literal }
        )
    }

    private fun org.jooq.Record.toDto(): EventResponseDto = EventResponseDto(
        id = get(EVENT_RESPONSES.ID)!!,
        eventId = get(EVENT_RESPONSES.EVENT_ID)!!,
        userId = get(EVENT_RESPONSES.USER_ID)!!,
        stage1Status = get(EVENT_RESPONSES.STAGE_1_STATUS)?.literal,
        finalStatus = get(EVENT_RESPONSES.FINAL_STATUS)?.literal,
        waitlistPosition = get(EVENT_RESPONSES.WAITLIST_POSITION),
        attended = get(EVENT_RESPONSES.ATTENDED),
        respondedAt = get(EVENT_RESPONSES.RESPONDED_AT)!!,
        confirmedAt = get(EVENT_RESPONSES.CONFIRMED_AT),
        createdAt = get(EVENT_RESPONSES.CREATED_AT)!!,
        updatedAt = get(EVENT_RESPONSES.UPDATED_AT)!!
    )
}
