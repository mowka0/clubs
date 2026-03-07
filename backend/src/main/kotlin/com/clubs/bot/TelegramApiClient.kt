package com.clubs.bot

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class TelegramApiClient(
    private val restTemplate: RestTemplate,
    @Value("\${telegram.bot-token:}") private val botToken: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val baseUrl get() = "https://api.telegram.org/bot$botToken"

    fun setWebhook(webhookUrl: String, secretToken: String? = null): Boolean {
        if (botToken.isBlank()) {
            log.warn("Bot token not configured, skipping setWebhook")
            return false
        }
        return try {
            val request = SetWebhookRequest(url = webhookUrl, secretToken = secretToken, dropPendingUpdates = true)
            @Suppress("UNCHECKED_CAST")
            val response = restTemplate.postForObject(
                "$baseUrl/setWebhook",
                HttpEntity(request, jsonHeaders()),
                Map::class.java
            ) as Map<String, Any>?
            val ok = response?.get("ok") == true
            if (ok) log.info("Webhook set successfully: $webhookUrl")
            else log.error("Failed to set webhook: ${response?.get("description")}")
            ok
        } catch (e: Exception) {
            log.error("Error setting webhook", e)
            false
        }
    }

    fun sendMessage(chatId: Long, text: String, replyMarkup: InlineKeyboardMarkup? = null) {
        if (botToken.isBlank()) return
        try {
            val request = SendMessageRequest(chatId = chatId, text = text, replyMarkup = replyMarkup)
            restTemplate.postForObject(
                "$baseUrl/sendMessage",
                HttpEntity(request, jsonHeaders()),
                Map::class.java
            )
        } catch (e: Exception) {
            log.error("Error sending message to chatId=$chatId", e)
        }
    }

    private fun jsonHeaders() = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }
}
