package com.clubs.auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class JwtServiceTest {

    // Secret must be at least 32 characters for HS256
    private val secret = "test-secret-key-at-least-32-chars-long"
    private val jwtService = JwtService(secret, expirationHours = 24)

    @Test
    fun `generateToken returns non-blank token`() {
        val token = jwtService.generateToken(UUID.randomUUID(), 123456L)
        assertTrue(token.isNotBlank())
    }

    @Test
    fun `validateToken returns claims for valid token`() {
        val userId = UUID.randomUUID()
        val telegramId = 987654L
        val token = jwtService.generateToken(userId, telegramId)
        val claims = jwtService.validateToken(token)
        assertNotNull(claims)
        assertEquals(userId.toString(), claims!!.subject)
        assertEquals(telegramId, (claims["telegram_id"] as? Number)?.toLong())
    }

    @Test
    fun `validateToken returns null for invalid token`() {
        assertNull(jwtService.validateToken("invalid.token.string"))
    }

    @Test
    fun `validateToken returns null for tampered token`() {
        val token = jwtService.generateToken(UUID.randomUUID(), 123L)
        val tampered = token.dropLast(5) + "XXXXX"
        assertNull(jwtService.validateToken(tampered))
    }

    @Test
    fun `getUserIdFromToken extracts correct userId`() {
        val userId = UUID.randomUUID()
        val token = jwtService.generateToken(userId, 123L)
        assertEquals(userId, jwtService.getUserIdFromToken(token))
    }

    @Test
    fun `getUserIdFromToken returns null for invalid token`() {
        assertNull(jwtService.getUserIdFromToken("bad.token"))
    }

    @Test
    fun `getTelegramIdFromToken extracts correct telegramId`() {
        val telegramId = 555666L
        val token = jwtService.generateToken(UUID.randomUUID(), telegramId)
        assertEquals(telegramId, jwtService.getTelegramIdFromToken(token))
    }

    @Test
    fun `token with different secret is invalid`() {
        val otherService = JwtService("other-secret-key-at-least-32-chars-long", 24)
        val token = otherService.generateToken(UUID.randomUUID(), 123L)
        assertNull(jwtService.validateToken(token))
    }
}
