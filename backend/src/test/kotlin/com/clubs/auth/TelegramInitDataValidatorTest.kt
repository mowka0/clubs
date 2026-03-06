package com.clubs.auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TelegramInitDataValidatorTest {

    private val botToken = "test-bot-token-for-testing"
    private val validator = TelegramInitDataValidator(botToken)

    @Test
    fun `validate returns false when initData has no hash`() {
        assertFalse(validator.validate("user=%7B%22id%22%3A123%7D&auth_date=1700000000"))
    }

    @Test
    fun `validate returns false with wrong hash`() {
        assertFalse(validator.validate("user=%7B%22id%22%3A123%7D&auth_date=1700000000&hash=wronghash"))
    }

    @Test
    fun `validate returns true with correct HMAC`() {
        val params = mapOf(
            "auth_date" to "1700000000",
            "user" to """{"id":123456,"username":"testuser","first_name":"Test"}"""
        )
        val dataCheckString = params.entries.sortedBy { it.key }
            .joinToString("\n") { "${it.key}=${it.value}" }

        val secretKey = hmacSha256("WebAppData".toByteArray(), botToken.toByteArray())
        val computedHash = hmacSha256(secretKey, dataCheckString.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val initData = params.entries.joinToString("&") { "${it.key}=${it.value}" } + "&hash=$computedHash"

        assertTrue(validator.validate(initData))
    }

    @Test
    fun `validate returns false when bot token is blank`() {
        val emptyTokenValidator = TelegramInitDataValidator("")
        assertFalse(emptyTokenValidator.validate("hash=abc"))
    }

    @Test
    fun `parseParams handles URL-encoded values`() {
        val initData = "user=%7B%22id%22%3A123%7D&auth_date=1700000000"
        val params = validator.parseParams(initData)
        assertEquals("""{"id":123}""", params["user"])
        assertEquals("1700000000", params["auth_date"])
    }

    @Test
    fun `extractTelegramUser returns null when user param missing`() {
        assertNull(validator.extractTelegramUser("auth_date=1700000000"))
    }

    @Test
    fun `extractTelegramUser parses user JSON correctly`() {
        val userJson = """{"id":123456,"username":"testuser","first_name":"Test","last_name":"User"}"""
        val initData = "user=${java.net.URLEncoder.encode(userJson, "UTF-8")}"
        val user = validator.extractTelegramUser(initData)
        assertNotNull(user)
        assertEquals(123456L, user!!.id)
        assertEquals("testuser", user.username)
        assertEquals("Test", user.first_name)
        assertEquals("User", user.last_name)
    }

    @Test
    fun `extractTelegramUser returns null on invalid JSON`() {
        val initData = "user=not-valid-json"
        assertNull(validator.extractTelegramUser(initData))
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
