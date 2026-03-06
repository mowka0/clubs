package com.clubs.user

import com.clubs.generated.jooq.tables.references.USERS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class UserRepository(private val dsl: DSLContext) {

    fun findByTelegramId(telegramId: Long): UserDto? {
        return dsl.selectFrom(USERS)
            .where(USERS.TELEGRAM_ID.eq(telegramId))
            .fetchOne()
            ?.toDto()
    }

    fun createOrUpdate(
        telegramId: Long,
        username: String?,
        firstName: String?,
        lastName: String?,
        avatarUrl: String?
    ): UserDto {
        val existing = findByTelegramId(telegramId)
        val now = OffsetDateTime.now()

        if (existing != null) {
            dsl.update(USERS)
                .set(USERS.USERNAME, username)
                .set(USERS.FIRST_NAME, firstName)
                .set(USERS.LAST_NAME, lastName)
                .set(USERS.UPDATED_AT, now)
                .where(USERS.TELEGRAM_ID.eq(telegramId))
                .execute()
            return existing.copy(
                username = username,
                firstName = firstName,
                lastName = lastName,
                updatedAt = now
            )
        }

        val newId = UUID.randomUUID()
        dsl.insertInto(USERS)
            .set(USERS.ID, newId)
            .set(USERS.TELEGRAM_ID, telegramId)
            .set(USERS.USERNAME, username)
            .set(USERS.FIRST_NAME, firstName)
            .set(USERS.LAST_NAME, lastName)
            .set(USERS.AVATAR_URL, avatarUrl)
            .set(USERS.CREATED_AT, now)
            .set(USERS.UPDATED_AT, now)
            .execute()

        return UserDto(
            id = newId,
            telegramId = telegramId,
            username = username,
            firstName = firstName,
            lastName = lastName,
            avatarUrl = avatarUrl,
            city = null,
            createdAt = now,
            updatedAt = now
        )
    }

    fun findById(id: UUID): UserDto? {
        return dsl.selectFrom(USERS)
            .where(USERS.ID.eq(id))
            .fetchOne()
            ?.toDto()
    }

    fun updateProfile(id: UUID, dto: UpdateUserDto): UserDto? {
        val now = OffsetDateTime.now()
        var update = dsl.update(USERS).set(USERS.UPDATED_AT, now)
        if (dto.city != null) update = update.set(USERS.CITY, dto.city)
        if (dto.firstName != null) update = update.set(USERS.FIRST_NAME, dto.firstName)
        if (dto.lastName != null) update = update.set(USERS.LAST_NAME, dto.lastName)
        if (dto.avatarUrl != null) update = update.set(USERS.AVATAR_URL, dto.avatarUrl)
        update.where(USERS.ID.eq(id)).execute()
        return findById(id)
    }

    private fun Record.toDto(): UserDto = UserDto(
        id = get(USERS.ID)!!,
        telegramId = get(USERS.TELEGRAM_ID)!!,
        username = get(USERS.USERNAME),
        firstName = get(USERS.FIRST_NAME),
        lastName = get(USERS.LAST_NAME),
        avatarUrl = get(USERS.AVATAR_URL),
        city = get(USERS.CITY),
        createdAt = get(USERS.CREATED_AT)!!,
        updatedAt = get(USERS.UPDATED_AT)!!
    )
}
