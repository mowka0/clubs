package com.clubs.bot

import com.clubs.club.ClubDto
import com.clubs.club.ClubRepository
import com.clubs.event.EventRepository
import com.clubs.event.EventResponseRepository
import com.clubs.reputation.ReputationService
import com.clubs.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class TelegramBotService(
    private val telegramApiClient: TelegramApiClient,
    @Value("\${telegram.bot-username:clubsapp}") private val botUsername: String,
    @Value("\${app.mini-app-url:https://t.me/clubsapp}") private val miniAppUrl: String,
    private val clubRepository: ClubRepository,
    private val eventRepository: EventRepository,
    private val eventResponseRepository: EventResponseRepository,
    private val reputationService: ReputationService,
    private val userService: UserService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm")

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
            "/кто_идет" -> handleWhoIsGoingCommand(chatId)
            "/мой_рейтинг" -> handleMyRatingCommand(chatId, message.from)
            "/события" -> handleEventsCommand(chatId)
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

    private fun handleWhoIsGoingCommand(chatId: Long) {
        val club = findClubByGroupChat(chatId) ?: run {
            telegramApiClient.sendMessage(chatId, "Этот чат не привязан к клубу. Попросите организатора привязать группу.")
            return
        }

        val upcomingEvents = eventRepository.findUpcomingByClub(club.id)
        if (upcomingEvents.isEmpty()) {
            telegramApiClient.sendMessage(chatId, "Нет предстоящих событий в клубе <b>${club.name}</b>.")
            return
        }

        val event = upcomingEvents.first()
        val counts = eventResponseRepository.countByStatus(event.id)
        val formattedDate = event.eventDatetime.format(dateFormatter)

        val text = "Ближайшее событие клуба <b>${club.name}</b>:\n\n" +
            "<b>${event.title}</b>\n" +
            "📅 $formattedDate\n\n" +
            "👍 Пойду: ${counts.going}\n" +
            "🤔 Возможно: ${counts.maybe}\n" +
            "✅ Подтвердили: ${event.confirmedCount}/${event.participantLimit}"

        val keyboard = InlineKeyboardMarkup(
            inlineKeyboard = listOf(
                listOf(InlineKeyboardButton(text = "Открыть событие", url = buildDeepLink("event_${event.id}")))
            )
        )
        telegramApiClient.sendMessage(chatId, text, keyboard)
    }

    private fun handleMyRatingCommand(chatId: Long, from: TelegramUser?) {
        if (from == null) {
            telegramApiClient.sendMessage(chatId, "Не удалось определить пользователя.")
            return
        }

        val club = findClubByGroupChat(chatId) ?: run {
            telegramApiClient.sendMessage(chatId, "Этот чат не привязан к клубу. Попросите организатора привязать группу.")
            return
        }

        val user = userService.findByTelegramId(from.id) ?: run {
            telegramApiClient.sendMessage(chatId, "${from.firstName}, вы ещё не зарегистрированы в приложении. Откройте Clubs, чтобы начать!")
            return
        }

        val reputation = reputationService.getUserClubReputation(user.id, club.id) ?: run {
            telegramApiClient.sendMessage(
                chatId,
                "${from.firstName}, у вас пока нет репутации в клубе <b>${club.name}</b>. Участвуйте в событиях, чтобы её получить!"
            )
            return
        }

        val fulfillmentPct = "%.0f".format(reputation.promiseFulfillmentPct)
        val text = "Рейтинг <b>${from.firstName}</b> в клубе <b>${club.name}</b>:\n\n" +
            "🔢 Индекс надёжности: <b>${reputation.reliabilityIndex}</b>\n" +
            "📊 Выполнение обещаний: <b>${fulfillmentPct}%</b>\n" +
            "🎲 Спонтанность: <b>${reputation.spontaneityCount}</b>"

        telegramApiClient.sendMessage(chatId, text)
    }

    private fun handleEventsCommand(chatId: Long) {
        val club = findClubByGroupChat(chatId) ?: run {
            telegramApiClient.sendMessage(chatId, "Этот чат не привязан к клубу. Попросите организатора привязать группу.")
            return
        }

        val upcomingEvents = eventRepository.findUpcomingByClub(club.id).take(5)
        if (upcomingEvents.isEmpty()) {
            telegramApiClient.sendMessage(chatId, "Нет предстоящих событий в клубе <b>${club.name}</b>.")
            return
        }

        val sb = StringBuilder("Предстоящие события клуба <b>${club.name}</b>:\n\n")
        upcomingEvents.forEachIndexed { idx, event ->
            val formattedDate = event.eventDatetime.format(dateFormatter)
            sb.append("${idx + 1}. <b>${event.title}</b> — $formattedDate\n")
            sb.append("   ${event.confirmedCount}/${event.participantLimit} участников\n\n")
        }

        val buttons = upcomingEvents.map { event ->
            listOf(InlineKeyboardButton(text = event.title, url = buildDeepLink("event_${event.id}")))
        }
        val keyboard = InlineKeyboardMarkup(inlineKeyboard = buttons)

        telegramApiClient.sendMessage(chatId, sb.toString().trimEnd(), keyboard)
    }

    private fun findClubByGroupChat(chatId: Long): ClubDto? =
        clubRepository.findByTelegramGroupId(chatId)

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
