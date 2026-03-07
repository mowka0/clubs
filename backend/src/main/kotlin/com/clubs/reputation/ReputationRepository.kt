package com.clubs.reputation

import com.clubs.generated.jooq.tables.references.USER_CLUB_REPUTATION
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class ReputationRepository(private val dsl: DSLContext) {

    fun findByUserAndClub(userId: UUID, clubId: UUID): ReputationDto? {
        return dsl.selectFrom(USER_CLUB_REPUTATION)
            .where(USER_CLUB_REPUTATION.USER_ID.eq(userId))
            .and(USER_CLUB_REPUTATION.CLUB_ID.eq(clubId))
            .fetchOne()
            ?.toDto()
    }

    fun findByClub(clubId: UUID): List<ReputationDto> {
        return dsl.selectFrom(USER_CLUB_REPUTATION)
            .where(USER_CLUB_REPUTATION.CLUB_ID.eq(clubId))
            .orderBy(USER_CLUB_REPUTATION.RELIABILITY_INDEX.desc())
            .fetch()
            .map { it.toDto() }
    }

    fun findAllByUser(userId: UUID): List<ReputationDto> {
        return dsl.selectFrom(USER_CLUB_REPUTATION)
            .where(USER_CLUB_REPUTATION.USER_ID.eq(userId))
            .fetch()
            .map { it.toDto() }
    }

    /**
     * Upserts reputation for user in club.
     * If row exists: increments all counters and recalculates promise_fulfillment_pct.
     * If not: inserts new row.
     */
    fun upsert(
        userId: UUID,
        clubId: UUID,
        reliabilityDelta: Int,
        totalConfirmedDelta: Int,
        totalAttendedDelta: Int,
        spontaneityDelta: Int
    ) {
        val existing = findByUserAndClub(userId, clubId)
        val now = OffsetDateTime.now()

        if (existing == null) {
            val newTotalConfirmed = totalConfirmedDelta
            val newTotalAttended = totalAttendedDelta
            val pct = if (newTotalConfirmed > 0) (newTotalAttended.toDouble() / newTotalConfirmed) * 100.0 else 0.0

            dsl.insertInto(USER_CLUB_REPUTATION)
                .set(USER_CLUB_REPUTATION.USER_ID, userId)
                .set(USER_CLUB_REPUTATION.CLUB_ID, clubId)
                .set(USER_CLUB_REPUTATION.RELIABILITY_INDEX, reliabilityDelta)
                .set(USER_CLUB_REPUTATION.PROMISE_FULFILLMENT_PCT, pct)
                .set(USER_CLUB_REPUTATION.SPONTANEITY_COUNT, spontaneityDelta)
                .set(USER_CLUB_REPUTATION.TOTAL_CONFIRMED, newTotalConfirmed)
                .set(USER_CLUB_REPUTATION.TOTAL_ATTENDED, newTotalAttended)
                .set(USER_CLUB_REPUTATION.CREATED_AT, now)
                .set(USER_CLUB_REPUTATION.UPDATED_AT, now)
                .execute()
        } else {
            val newTotalConfirmed = existing.totalConfirmed + totalConfirmedDelta
            val newTotalAttended = existing.totalAttended + totalAttendedDelta
            val pct = if (newTotalConfirmed > 0) (newTotalAttended.toDouble() / newTotalConfirmed) * 100.0 else 0.0

            dsl.update(USER_CLUB_REPUTATION)
                .set(USER_CLUB_REPUTATION.RELIABILITY_INDEX, existing.reliabilityIndex + reliabilityDelta)
                .set(USER_CLUB_REPUTATION.PROMISE_FULFILLMENT_PCT, pct)
                .set(USER_CLUB_REPUTATION.SPONTANEITY_COUNT, existing.spontaneityCount + spontaneityDelta)
                .set(USER_CLUB_REPUTATION.TOTAL_CONFIRMED, newTotalConfirmed)
                .set(USER_CLUB_REPUTATION.TOTAL_ATTENDED, newTotalAttended)
                .set(USER_CLUB_REPUTATION.UPDATED_AT, now)
                .where(USER_CLUB_REPUTATION.USER_ID.eq(userId))
                .and(USER_CLUB_REPUTATION.CLUB_ID.eq(clubId))
                .execute()
        }
    }

    private fun org.jooq.Record.toDto() = ReputationDto(
        id = get(USER_CLUB_REPUTATION.ID)!!,
        userId = get(USER_CLUB_REPUTATION.USER_ID)!!,
        clubId = get(USER_CLUB_REPUTATION.CLUB_ID)!!,
        reliabilityIndex = get(USER_CLUB_REPUTATION.RELIABILITY_INDEX)!!,
        promiseFulfillmentPct = get(USER_CLUB_REPUTATION.PROMISE_FULFILLMENT_PCT)!!,
        spontaneityCount = get(USER_CLUB_REPUTATION.SPONTANEITY_COUNT)!!,
        totalConfirmed = get(USER_CLUB_REPUTATION.TOTAL_CONFIRMED)!!,
        totalAttended = get(USER_CLUB_REPUTATION.TOTAL_ATTENDED)!!,
        createdAt = get(USER_CLUB_REPUTATION.CREATED_AT)!!,
        updatedAt = get(USER_CLUB_REPUTATION.UPDATED_AT)!!
    )
}
