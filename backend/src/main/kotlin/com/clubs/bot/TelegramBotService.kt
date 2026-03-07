package com.clubs.bot

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class TelegramBotService(
    private val telegramApiClient: TelegramApiClient,
    @Value("\${telegram.bot-username:clubsapp}") private val botUsername: String,
    @Value("\${app.mini-app-url:https://t.me/clubsapp}") private val miniAppUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun processUpdate(update: TelegramUpdate) {
        try {
            when {
                update.message != null -> handleMessage(update.message)
                update.callbackQuery != null -> handleCallbackQuery(update.callbackQuery)
                update.myChatMember != null -> handleBotMembershipChanged(update.myChatMember)
            }
        } catch (e: Exception) {
            log.error("Error processing update ${update.updateId}", e)
        }
    }

    private fun handleMessage(message: TelegramMessage) {
        val text = message.text ?: return
        val chatId = message.chat.id
        val command = text.split(" ").first().split("@").first()
        when (command) {
            "/start", "/help" -> handleStartCommand(chatId, message.from)
            else -> log.debug("Unhandled command '{}' in chat {}", command, chatId)
        }
    }

    private fun handleStartCommand(chatId: Long, from: TelegramUser?) {
        val name = from?.firstName ?: "пользователь"
        val text = "Привет, <b>$name</b>!\n\n" +
            "Clubs — приложение для поиска и организации клубов по интересам.\n\n" +
            "Нажми кнопку ниже, чтобы открыть приложение:"
        val keyboard = InlineKeyboardMarkup(
            inlineKeyboard = listOf(
                listOf(InlineKeyboardButton(text = "Открыть Clubs", url = miniAppUrl))
            )
        )
        telegramApiClient.sendMessage(chatId, text, keyboard)
    }

    private fun handleCallbackQuery(callbackQuery: TelegramCallbackQuery) {
        log.debug("Callback query from userId={}: data={}", callbackQuery.from.id, callbackQuery.data)
    }

    private fun handleBotMembershipChanged(update: TelegramChatMemberUpdated) {
        val newStatus = update.newChatMember?.status
        val chatId = update.chat.id
        log.info("Bot membership changed to '{}' in chat {} ({})", newStatus, chatId, update.chat.title)
    }

    fun buildDeepLink(param: String): String = "$miniAppUrl?startapp=$param"
}
