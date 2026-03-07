package com.clubs.bot

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class TelegramBotInitializer(
    private val telegramApiClient: TelegramApiClient,
    @Value("\${telegram.webhook-url:}") private val webhookUrl: String,
    @Value("\${telegram.webhook-secret-token:}") private val webhookSecretToken: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (webhookUrl.isBlank()) {
            log.info("Telegram webhook URL not configured — skipping webhook registration (set telegram.webhook-url to enable)")
            return
        }
        val secretToken = webhookSecretToken.ifBlank { null }
        val success = telegramApiClient.setWebhook(webhookUrl, secretToken)
        if (!success) {
            log.warn("Failed to register Telegram webhook at {}. Bot will not receive updates.", webhookUrl)
        }
    }
}
