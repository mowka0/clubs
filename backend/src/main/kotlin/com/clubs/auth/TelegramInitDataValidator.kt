package com.clubs.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class TelegramInitDataValidator(
    @Value("\${telegram.bot-token}") private val botToken: String
) {

    fun validate(initData: String): Boolean {
        if (botToken.isBlank()) return false
        val params = parseParams(initData)
        val hash = params["hash"] ?: return false

        val dataCheckString = params
            .filterKeys { it != "hash" }
            .entries
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key}=${it.value}" }

        val secretKey = hmacSha256("WebAppData".toByteArray(), botToken.toByteArray())
        val computed = hmacSha256(secretKey, dataCheckString.toByteArray())
        val computedHex = computed.joinToString("") { "%02x".format(it) }

        return computedHex == hash
    }

    fun parseParams(initData: String): Map<String, String> {
        return initData.split("&").associate { pair ->
            val idx = pair.indexOf('=')
            if (idx == -1) pair to ""
            else {
                val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
                val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                key to value
            }
        }
    }

    fun extractTelegramUser(initData: String): TelegramUserData? {
        val params = parseParams(initData)
        val userJson = params["user"] ?: return null
        return try {
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .readValue(userJson, TelegramUserData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}

data class TelegramUserData(
    val id: Long = 0,
    val username: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val photo_url: String? = null
)
