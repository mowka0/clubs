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

    fun createChatInviteLink(chatId: Long): String? {
        if (botToken.isBlank()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = restTemplate.postForObject(
                "$baseUrl/createChatInviteLink",
                HttpEntity(mapOf("chat_id" to chatId), jsonHeaders()),
                Map::class.java
            ) as Map<String, Any>?
            val result = response?.get("result") as? Map<*, *>
            result?.get("invite_link") as? String
        } catch (e: Exception) {
            log.error("Error creating chat invite link for chatId=$chatId", e)
            null
        }
    }

    fun createInvoiceLink(
        title: String,
        description: String,
        payload: String,
        amountStars: Int
    ): String? {
        if (botToken.isBlank()) return null
        return try {
            val body = mapOf(
                "title" to title,
                "description" to description,
                "payload" to payload,
                "provider_token" to "",
                "currency" to "XTR",
                "prices" to listOf(mapOf("label" to title, "amount" to amountStars))
            )
            @Suppress("UNCHECKED_CAST")
            val response = restTemplate.postForObject(
                "$baseUrl/createInvoiceLink",
                HttpEntity(body, jsonHeaders()),
                Map::class.java
            ) as Map<String, Any>?
            response?.get("result") as? String
        } catch (e: Exception) {
            log.error("Error creating invoice link", e)
            null
        }
    }

    fun answerPreCheckoutQuery(preCheckoutQueryId: String, ok: Boolean, errorMessage: String? = null) {
        if (botToken.isBlank()) return
        try {
            val body = mutableMapOf<String, Any>(
                "pre_checkout_query_id" to preCheckoutQueryId,
                "ok" to ok
            )
            if (!ok && errorMessage != null) {
                body["error_message"] = errorMessage
            }
            restTemplate.postForObject(
                "$baseUrl/answerPreCheckoutQuery",
                HttpEntity(body, jsonHeaders()),
                Map::class.java
            )
        } catch (e: Exception) {
            log.error("Error answering pre_checkout_query $preCheckoutQueryId", e)
        }
    }

    private fun jsonHeaders() = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }
}
