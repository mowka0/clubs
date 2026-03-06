package com.clubs.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-hours:24}") private val expirationHours: Long
) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    fun generateToken(userId: UUID, telegramId: Long): String {
        val now = Date()
        val expiry = Date(now.time + expirationHours * 3600 * 1000)
        return Jwts.builder()
            .subject(userId.toString())
            .claim("telegram_id", telegramId)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Claims? {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: Exception) {
            null
        }
    }

    fun getUserIdFromToken(token: String): UUID? {
        return validateToken(token)?.subject?.let { UUID.fromString(it) }
    }

    fun getTelegramIdFromToken(token: String): Long? {
        return validateToken(token)?.get("telegram_id", Long::class.java)
    }
}
