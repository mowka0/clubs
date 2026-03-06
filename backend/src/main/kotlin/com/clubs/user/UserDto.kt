package com.clubs.user

import java.time.OffsetDateTime
import java.util.UUID

data class UserDto(
    val id: UUID,
    val telegramId: Long,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val avatarUrl: String?,
    val city: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class UpdateUserDto(
    val city: String?,
    val firstName: String?,
    val lastName: String?,
    val avatarUrl: String?
)
