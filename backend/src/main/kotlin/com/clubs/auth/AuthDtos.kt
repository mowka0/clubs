package com.clubs.auth

data class AuthRequest(val initData: String)

data class AuthResponse(val token: String, val user: AuthUserDto)

data class AuthUserDto(
    val id: String,
    val telegramId: Long,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val avatarUrl: String?
)
