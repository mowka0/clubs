package com.clubs.auth

import com.clubs.user.UserDto

data class AuthRequest(val initData: String)

data class AuthResponse(val token: String, val user: UserDto)
