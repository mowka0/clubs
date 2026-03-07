package com.clubs.notification

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * High-level notification service. Provides typed notification methods that are
 * enqueued into Redis for rate-limited delivery via Telegram Bot API.
 *
 * Also supports group notifications (reused by TASK-031 group messages).
 */
@Service
class NotificationService(
    private val notificationQueueService: NotificationQueueService,
    @Value("\${app.mini-app-url:https://t.me/clubsapp}") private val miniAppUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ─── Core send methods ───────────────────────────────────────────────────

    fun sendPersonalNotification(
        telegramId: Long,
        text: String,
        buttonText: String? = null,
        buttonUrl: String? = null
    ) {
        try {
            notificationQueueService.enqueue(telegramId, text, buttonText, buttonUrl)
        } catch (e: Exception) {
            log.error("Failed to enqueue personal notification for telegramId={}", telegramId, e)
        }
    }

    fun sendGroupNotification(
        chatId: Long,
        text: String,
        buttonText: String? = null,
        buttonUrl: String? = null
    ) {
        try {
            notificationQueueService.enqueue(chatId, text, buttonText, buttonUrl)
        } catch (e: Exception) {
            log.error("Failed to enqueue group notification for chatId={}", chatId, e)
        }
    }

    fun buildDeepLink(param: String): String = "$miniAppUrl?startapp=$param"

    // ─── Application notifications ───────────────────────────────────────────

    fun notifyApplicationApproved(telegramId: Long, clubName: String, clubId: UUID) {
        sendPersonalNotification(
            telegramId = telegramId,
            text = "Ваша заявка в клуб <b>$clubName</b> одобрена! Добро пожаловать!",
            buttonText = "Открыть клуб",
            buttonUrl = buildDeepLink("club_$clubId")
        )
    }

    fun notifyApplicationRejected(telegramId: Long, clubName: String, reason: String?) {
        val text = if (reason != null) {
            "Ваша заявка в клуб <b>$clubName</b> отклонена.\nПричина: $reason"
        } else {
            "Ваша заявка в клуб <b>$clubName</b> отклонена."
        }
        sendPersonalNotification(telegramId, text)
    }

    // ─── Event Stage 2 notifications ─────────────────────────────────────────

    fun notifyStage2Started(telegramId: Long, eventTitle: String, eventId: UUID) {
        sendPersonalNotification(
            telegramId = telegramId,
            text = "Этап 2 начался! Подтвердите участие в событии <b>$eventTitle</b>.\n\nПоторопитесь — мест ограничено!",
            buttonText = "Подтвердить участие",
            buttonUrl = buildDeepLink("event_$eventId")
        )
    }

    fun notifyWaitlisted(telegramId: Long, eventTitle: String, eventId: UUID, position: Int) {
        sendPersonalNotification(
            telegramId = telegramId,
            text = "Вы в листе ожидания (позиция $position) для события <b>$eventTitle</b>.\n\nМы уведомим вас, если место освободится.",
            buttonText = "Открыть событие",
            buttonUrl = buildDeepLink("event_$eventId")
        )
    }

    fun notifySlotFreed(telegramId: Long, eventTitle: String, eventId: UUID) {
        sendPersonalNotification(
            telegramId = telegramId,
            text = "Место освободилось! Вы подтверждены для события <b>$eventTitle</b>.",
            buttonText = "Открыть событие",
            buttonUrl = buildDeepLink("event_$eventId")
        )
    }

    // ─── Attendance notifications ─────────────────────────────────────────────

    fun notifyMarkedAbsent(telegramId: Long, eventTitle: String, eventId: UUID) {
        sendPersonalNotification(
            telegramId = telegramId,
            text = "Организатор отметил вас как не пришедшего на событие <b>$eventTitle</b>.\n\nЕсли это ошибка — оспорьте отметку.",
            buttonText = "Оспорить",
            buttonUrl = buildDeepLink("event_$eventId")
        )
    }

    // ─── Group (chat) notifications ───────────────────────────────────────────

    fun notifyGroupEventCreated(chatId: Long, eventTitle: String, eventId: UUID, eventDateFormatted: String, location: String?) {
        val locationLine = if (location != null) "\n📍 $location" else ""
        val text = "📣 <b>Новое событие!</b>\n\n" +
            "<b>$eventTitle</b>\n" +
            "📅 $eventDateFormatted$locationLine\n\n" +
            "Проголосуйте в приложении, чтобы записаться."
        sendGroupNotification(
            chatId = chatId,
            text = text,
            buttonText = "Открыть событие",
            buttonUrl = buildDeepLink("event_$eventId")
        )
    }

    fun notifyGroupVotingOpened(chatId: Long, eventTitle: String, eventId: UUID, eventDateFormatted: String) {
        val text = "🗳 <b>Началось голосование!</b>\n\n" +
            "Событие: <b>$eventTitle</b>\n" +
            "📅 $eventDateFormatted\n\n" +
            "Отметьтесь: пойдёте или нет?"
        sendGroupNotification(
            chatId = chatId,
            text = text,
            buttonText = "Проголосовать",
            buttonUrl = buildDeepLink("event_$eventId")
        )
    }

    fun notifyGroupNewMember(chatId: Long, firstName: String, clubId: UUID) {
        val text = "👋 <b>$firstName</b> только что вступил в клуб. Добро пожаловать!"
        sendGroupNotification(
            chatId = chatId,
            text = text,
            buttonText = "Открыть клуб",
            buttonUrl = buildDeepLink("club_$clubId")
        )
    }

    // ─── Subscription notifications ───────────────────────────────────────────

    fun notifySubscriptionExpiringSoon(telegramId: Long, clubName: String, clubId: UUID, daysLeft: Int) {
        sendPersonalNotification(
            telegramId = telegramId,
            text = "Через $daysLeft дн. истекает подписка на клуб <b>$clubName</b>.\n\nПродлите подписку, чтобы не потерять доступ.",
            buttonText = "Открыть клуб",
            buttonUrl = buildDeepLink("club_$clubId")
        )
    }
}
