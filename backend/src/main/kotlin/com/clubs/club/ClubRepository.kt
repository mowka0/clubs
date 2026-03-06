package com.clubs.club

import com.clubs.generated.jooq.enums.ClubAccessType
import com.clubs.generated.jooq.enums.ClubCategory
import com.clubs.generated.jooq.tables.references.CLUBS
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class ClubRepository(private val dsl: DSLContext) {

    fun create(dto: CreateClubDto): ClubDto {
        val now = OffsetDateTime.now()
        val id = UUID.randomUUID()
        dsl.insertInto(CLUBS)
            .set(CLUBS.ID, id)
            .set(CLUBS.OWNER_ID, dto.ownerId)
            .set(CLUBS.NAME, dto.name)
            .set(CLUBS.DESCRIPTION, dto.description)
            .set(CLUBS.CITY, dto.city)
            .set(CLUBS.CATEGORY, ClubCategory.valueOf(dto.category))
            .set(CLUBS.ACCESS_TYPE, ClubAccessType.valueOf(dto.accessType))
            .set(CLUBS.MEMBER_LIMIT, dto.memberLimit)
            .set(CLUBS.SUBSCRIPTION_PRICE, dto.subscriptionPrice)
            .set(CLUBS.AVATAR_URL, dto.avatarUrl)
            .set(CLUBS.COVER_URL, dto.coverUrl)
            .set(CLUBS.RULES, dto.rules)
            .set(CLUBS.APPLICATION_QUESTION, dto.applicationQuestion)
            .set(CLUBS.CREATED_AT, now)
            .set(CLUBS.UPDATED_AT, now)
            .execute()
        return findById(id)!!
    }

    fun findById(id: UUID): ClubDto? {
        return dsl.selectFrom(CLUBS)
            .where(CLUBS.ID.eq(id))
            .fetchOne()
            ?.toDto()
    }

    fun findAll(filters: ClubFilters, page: Int = 0, size: Int = 20, sort: String = "newest"): List<ClubDto> {
        val conditions = buildFilterConditions(filters)
        val orderBy = buildSortOrder(sort)
        return dsl.selectFrom(CLUBS)
            .where(CLUBS.IS_ACTIVE.isTrue)
            .and(CLUBS.ACCESS_TYPE.ne(ClubAccessType.`private`))
            .and(DSL.and(conditions))
            .orderBy(orderBy)
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toDto() }
    }

    fun countAll(filters: ClubFilters): Long {
        val conditions = buildFilterConditions(filters)
        return dsl.fetchCount(
            dsl.selectFrom(CLUBS)
                .where(CLUBS.IS_ACTIVE.isTrue)
                .and(CLUBS.ACCESS_TYPE.ne(ClubAccessType.`private`))
                .and(DSL.and(conditions))
        ).toLong()
    }

    private fun buildSortOrder(sort: String): SortField<*> = when (sort) {
        "price_asc" -> CLUBS.SUBSCRIPTION_PRICE.asc()
        "price_desc" -> CLUBS.SUBSCRIPTION_PRICE.desc()
        else -> CLUBS.CREATED_AT.desc()
    }

    fun search(query: String): List<ClubDto> {
        return dsl.selectFrom(CLUBS)
            .where(CLUBS.IS_ACTIVE.isTrue)
            .and(CLUBS.ACCESS_TYPE.ne(ClubAccessType.`private`))
            .and(
                CLUBS.NAME.likeIgnoreCase("%$query%")
                    .or(CLUBS.DESCRIPTION.likeIgnoreCase("%$query%"))
            )
            .fetch()
            .map { it.toDto() }
    }

    fun update(id: UUID, dto: UpdateClubDto): ClubDto {
        val now = OffsetDateTime.now()
        val update = dsl.update(CLUBS).set(CLUBS.UPDATED_AT, now)
        dto.name?.let { update.set(CLUBS.NAME, it) }
        dto.description?.let { update.set(CLUBS.DESCRIPTION, it) }
        dto.city?.let { update.set(CLUBS.CITY, it) }
        dto.category?.let { update.set(CLUBS.CATEGORY, ClubCategory.valueOf(it)) }
        dto.accessType?.let { update.set(CLUBS.ACCESS_TYPE, ClubAccessType.valueOf(it)) }
        dto.memberLimit?.let { update.set(CLUBS.MEMBER_LIMIT, it) }
        dto.subscriptionPrice?.let { update.set(CLUBS.SUBSCRIPTION_PRICE, it) }
        dto.avatarUrl?.let { update.set(CLUBS.AVATAR_URL, it) }
        dto.coverUrl?.let { update.set(CLUBS.COVER_URL, it) }
        dto.rules?.let { update.set(CLUBS.RULES, it) }
        dto.applicationQuestion?.let { update.set(CLUBS.APPLICATION_QUESTION, it) }
        dto.telegramGroupId?.let { update.set(CLUBS.TELEGRAM_GROUP_ID, it) }
        update.where(CLUBS.ID.eq(id)).execute()
        return findById(id) ?: throw IllegalStateException("Club $id not found after update")
    }

    fun softDelete(id: UUID) {
        dsl.update(CLUBS)
            .set(CLUBS.IS_ACTIVE, false)
            .set(CLUBS.UPDATED_AT, OffsetDateTime.now())
            .where(CLUBS.ID.eq(id))
            .execute()
    }

    fun countByOwner(ownerId: UUID): Int {
        return dsl.fetchCount(
            dsl.selectFrom(CLUBS)
                .where(CLUBS.OWNER_ID.eq(ownerId))
                .and(CLUBS.IS_ACTIVE.isTrue)
        )
    }

    internal fun buildFilterConditions(filters: ClubFilters): List<Condition> {
        val conditions = mutableListOf<Condition>()
        filters.city?.let { conditions.add(CLUBS.CITY.eq(it)) }
        filters.category?.let { conditions.add(CLUBS.CATEGORY.eq(ClubCategory.valueOf(it))) }
        filters.accessType?.let { conditions.add(CLUBS.ACCESS_TYPE.eq(ClubAccessType.valueOf(it))) }
        filters.priceMin?.let { conditions.add(CLUBS.SUBSCRIPTION_PRICE.ge(it)) }
        filters.priceMax?.let { conditions.add(CLUBS.SUBSCRIPTION_PRICE.le(it)) }
        filters.memberLimitMin?.let { conditions.add(CLUBS.MEMBER_LIMIT.ge(it)) }
        filters.memberLimitMax?.let { conditions.add(CLUBS.MEMBER_LIMIT.le(it)) }
        filters.search?.let {
            conditions.add(
                CLUBS.NAME.likeIgnoreCase("%$it%")
                    .or(CLUBS.DESCRIPTION.likeIgnoreCase("%$it%"))
            )
        }
        return conditions
    }

    private fun org.jooq.Record.toDto(): ClubDto = ClubDto(
        id = get(CLUBS.ID)!!,
        ownerId = get(CLUBS.OWNER_ID)!!,
        name = get(CLUBS.NAME)!!,
        description = get(CLUBS.DESCRIPTION),
        city = get(CLUBS.CITY),
        category = get(CLUBS.CATEGORY)!!.literal,
        accessType = get(CLUBS.ACCESS_TYPE)!!.literal,
        memberLimit = get(CLUBS.MEMBER_LIMIT)!!,
        subscriptionPrice = get(CLUBS.SUBSCRIPTION_PRICE)!!,
        avatarUrl = get(CLUBS.AVATAR_URL),
        coverUrl = get(CLUBS.COVER_URL),
        rules = get(CLUBS.RULES),
        applicationQuestion = get(CLUBS.APPLICATION_QUESTION),
        telegramGroupId = get(CLUBS.TELEGRAM_GROUP_ID),
        activityRating = get(CLUBS.ACTIVITY_RATING)!!,
        confirmedCount = get(CLUBS.CONFIRMED_COUNT)!!,
        isActive = get(CLUBS.IS_ACTIVE)!!,
        createdAt = get(CLUBS.CREATED_AT)!!,
        updatedAt = get(CLUBS.UPDATED_AT)!!
    )
}
