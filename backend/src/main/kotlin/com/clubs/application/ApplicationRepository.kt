package com.clubs.application

import com.clubs.generated.jooq.enums.ApplicationStatus
import com.clubs.generated.jooq.tables.references.APPLICATIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class ApplicationRepository(private val dsl: DSLContext) {

    fun create(userId: UUID, clubId: UUID, answerText: String?): ApplicationDto {
        val now = OffsetDateTime.now()
        val id = UUID.randomUUID()
        dsl.insertInto(APPLICATIONS)
            .set(APPLICATIONS.ID, id)
            .set(APPLICATIONS.USER_ID, userId)
            .set(APPLICATIONS.CLUB_ID, clubId)
            .set(APPLICATIONS.ANSWER_TEXT, answerText)
            .set(APPLICATIONS.STATUS, ApplicationStatus.pending)
            .set(APPLICATIONS.CREATED_AT, now)
            .set(APPLICATIONS.UPDATED_AT, now)
            .execute()
        return findById(id)!!
    }

    fun findById(id: UUID): ApplicationDto? {
        return dsl.selectFrom(APPLICATIONS)
            .where(APPLICATIONS.ID.eq(id))
            .fetchOne()
            ?.toDto()
    }

    fun findByUserAndClub(userId: UUID, clubId: UUID): ApplicationDto? {
        return dsl.selectFrom(APPLICATIONS)
            .where(APPLICATIONS.USER_ID.eq(userId))
            .and(APPLICATIONS.CLUB_ID.eq(clubId))
            .orderBy(APPLICATIONS.CREATED_AT.desc())
            .limit(1)
            .fetchOne()
            ?.toDto()
    }

    fun findByClubId(clubId: UUID, status: ApplicationStatus? = null): List<ApplicationDto> {
        val query = dsl.selectFrom(APPLICATIONS)
            .where(APPLICATIONS.CLUB_ID.eq(clubId))
        if (status != null) {
            query.and(APPLICATIONS.STATUS.eq(status))
        }
        return query.orderBy(APPLICATIONS.CREATED_AT.asc()).fetch().map { it.toDto() }
    }

    fun findPendingByUserAndClub(userId: UUID, clubId: UUID): ApplicationDto? {
        return dsl.selectFrom(APPLICATIONS)
            .where(APPLICATIONS.USER_ID.eq(userId))
            .and(APPLICATIONS.CLUB_ID.eq(clubId))
            .and(APPLICATIONS.STATUS.eq(ApplicationStatus.pending))
            .fetchOne()
            ?.toDto()
    }

    fun updateStatus(id: UUID, status: ApplicationStatus, rejectionReason: String? = null) {
        dsl.update(APPLICATIONS)
            .set(APPLICATIONS.STATUS, status)
            .set(APPLICATIONS.REJECTION_REASON, rejectionReason)
            .set(APPLICATIONS.UPDATED_AT, OffsetDateTime.now())
            .where(APPLICATIONS.ID.eq(id))
            .execute()
    }

    fun findAllByUser(userId: UUID): List<ApplicationDto> {
        return dsl.selectFrom(APPLICATIONS)
            .where(APPLICATIONS.USER_ID.eq(userId))
            .orderBy(APPLICATIONS.CREATED_AT.desc())
            .fetch()
            .map { it.toDto() }
    }

    fun findPendingOlderThan(cutoff: OffsetDateTime): List<ApplicationDto> {
        return dsl.selectFrom(APPLICATIONS)
            .where(APPLICATIONS.STATUS.eq(ApplicationStatus.pending))
            .and(APPLICATIONS.CREATED_AT.lt(cutoff))
            .fetch()
            .map { it.toDto() }
    }

    private fun org.jooq.Record.toDto(): ApplicationDto = ApplicationDto(
        id = get(APPLICATIONS.ID)!!,
        userId = get(APPLICATIONS.USER_ID)!!,
        clubId = get(APPLICATIONS.CLUB_ID)!!,
        answerText = get(APPLICATIONS.ANSWER_TEXT),
        status = get(APPLICATIONS.STATUS)!!.literal,
        rejectionReason = get(APPLICATIONS.REJECTION_REASON),
        createdAt = get(APPLICATIONS.CREATED_AT)!!,
        updatedAt = get(APPLICATIONS.UPDATED_AT)!!
    )
}
