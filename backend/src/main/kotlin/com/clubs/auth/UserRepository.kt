package com.clubs.auth

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
class UserRepository(private val dsl: DSLContext) {

    private val users = DSL.table("users")
    private val idField = DSL.field("id", UUID::class.java)
    private val telegramIdField = DSL.field("telegram_id", Long::class.javaObjectType)
    private val usernameField = DSL.field("username", String::class.java)
    private val firstNameField = DSL.field("first_name", String::class.java)
    private val lastNameField = DSL.field("last_name", String::class.java)
    private val avatarUrlField = DSL.field("avatar_url", String::class.java)
    private val createdAtField = DSL.field("created_at", LocalDateTime::class.java)
    private val updatedAtField = DSL.field("updated_at", LocalDateTime::class.java)

    fun findByTelegramId(telegramId: Long): AuthUserDto? {
        return dsl.select(
            idField, telegramIdField, usernameField, firstNameField, lastNameField, avatarUrlField
        )
            .from(users)
            .where(telegramIdField.eq(telegramId))
            .fetchOne { record ->
                AuthUserDto(
                    id = record[idField].toString(),
                    telegramId = record[telegramIdField] ?: telegramId,
                    username = record[usernameField],
                    firstName = record[firstNameField],
                    lastName = record[lastNameField],
                    avatarUrl = record[avatarUrlField]
                )
            }
    }

    fun createOrUpdate(
        telegramId: Long,
        username: String?,
        firstName: String?,
        lastName: String?,
        avatarUrl: String?
    ): AuthUserDto {
        val now = LocalDateTime.now()
        val existing = findByTelegramId(telegramId)

        if (existing != null) {
            dsl.update(users)
                .set(usernameField, username)
                .set(firstNameField, firstName)
                .set(lastNameField, lastName)
                .set(updatedAtField, now)
                .where(telegramIdField.eq(telegramId))
                .execute()
            return existing.copy(username = username, firstName = firstName, lastName = lastName)
        }

        val newId = UUID.randomUUID()
        dsl.insertInto(users)
            .set(idField, newId)
            .set(telegramIdField, telegramId)
            .set(usernameField, username)
            .set(firstNameField, firstName)
            .set(lastNameField, lastName)
            .set(avatarUrlField, avatarUrl)
            .set(createdAtField, now)
            .set(updatedAtField, now)
            .execute()

        return AuthUserDto(
            id = newId.toString(),
            telegramId = telegramId,
            username = username,
            firstName = firstName,
            lastName = lastName,
            avatarUrl = avatarUrl
        )
    }

    fun findById(id: UUID): AuthUserDto? {
        return dsl.select(
            idField, telegramIdField, usernameField, firstNameField, lastNameField, avatarUrlField
        )
            .from(users)
            .where(idField.eq(id))
            .fetchOne { record ->
                AuthUserDto(
                    id = record[idField].toString(),
                    telegramId = record[telegramIdField] ?: 0L,
                    username = record[usernameField],
                    firstName = record[firstNameField],
                    lastName = record[lastNameField],
                    avatarUrl = record[avatarUrlField]
                )
            }
    }
}
