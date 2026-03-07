package com.clubs.invite

import com.clubs.generated.jooq.tables.references.INVITE_LINKS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class InviteLinkRepository(private val dsl: DSLContext) {

    fun create(clubId: UUID, code: String, isSingleUse: Boolean, createdBy: UUID): InviteLinkDto {
        val now = OffsetDateTime.now()
        val id = UUID.randomUUID()
        dsl.insertInto(INVITE_LINKS)
            .set(INVITE_LINKS.ID, id)
            .set(INVITE_LINKS.CLUB_ID, clubId)
            .set(INVITE_LINKS.CODE, code)
            .set(INVITE_LINKS.IS_SINGLE_USE, isSingleUse)
            .set(INVITE_LINKS.IS_USED, false)
            .set(INVITE_LINKS.CREATED_BY, createdBy)
            .set(INVITE_LINKS.CREATED_AT, now)
            .execute()
        return findByCode(code)!!
    }

    fun findByCode(code: String): InviteLinkDto? {
        return dsl.selectFrom(INVITE_LINKS)
            .where(INVITE_LINKS.CODE.eq(code))
            .fetchOne()
            ?.let { r ->
                InviteLinkDto(
                    id = r.id!!,
                    clubId = r.clubId!!,
                    code = r.code!!,
                    isSingleUse = r.isSingleUse!!,
                    isUsed = r.isUsed!!,
                    createdBy = r.createdBy!!,
                    createdAt = r.createdAt!!,
                    usedAt = r.usedAt
                )
            }
    }

    fun markUsed(code: String) {
        dsl.update(INVITE_LINKS)
            .set(INVITE_LINKS.IS_USED, true)
            .set(INVITE_LINKS.USED_AT, OffsetDateTime.now())
            .where(INVITE_LINKS.CODE.eq(code))
            .execute()
    }
}
