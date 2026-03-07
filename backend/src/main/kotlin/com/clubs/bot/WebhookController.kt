package com.clubs.bot

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/webhook")
class WebhookController(
    private val telegramBotService: TelegramBotService,
    @Value("\${telegram.webhook-secret-token:}") private val webhookSecretToken: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/telegram")
    fun handleTelegramWebhook(
        @RequestBody update: TelegramUpdate,
        @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) secretToken: String?
    ): ResponseEntity<Void> {
        if (webhookSecretToken.isNotBlank() && secretToken != webhookSecretToken) {
            log.warn("Received webhook request with invalid secret token")
            return ResponseEntity.status(403).build()
        }
        telegramBotService.processUpdate(update)
        return ResponseEntity.ok().build()
    }
}
