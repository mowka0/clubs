package com.clubs.auth

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val telegramValidator: TelegramInitDataValidator,
    private val jwtService: JwtService,
    private val userRepository: UserRepository
) {

    @PostMapping("/telegram")
    fun authenticate(@RequestBody request: AuthRequest): ResponseEntity<AuthResponse> {
        if (!telegramValidator.validate(request.initData)) {
            return ResponseEntity.status(401).build()
        }

        val telegramUser = telegramValidator.extractTelegramUser(request.initData)
            ?: return ResponseEntity.status(400).build()

        val user = userRepository.createOrUpdate(
            telegramId = telegramUser.id,
            username = telegramUser.username,
            firstName = telegramUser.first_name,
            lastName = telegramUser.last_name,
            avatarUrl = telegramUser.photo_url
        )

        val token = jwtService.generateToken(
            userId = java.util.UUID.fromString(user.id),
            telegramId = telegramUser.id
        )

        return ResponseEntity.ok(AuthResponse(token = token, user = user))
    }
}
