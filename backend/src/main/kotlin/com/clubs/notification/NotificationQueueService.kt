package com.clubs.notification

import com.clubs.bot.InlineKeyboardButton
import com.clubs.bot.InlineKeyboardMarkup
import com.clubs.bot.TelegramApiClient
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Redis-backed notification queue with Telegram API rate limiting (max 25 msg/sec).
 * Push to queue via [enqueue]; a scheduled worker processes up to 25 messages per second.
 */
@Service
class NotificationQueueService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val telegramApiClient: TelegramApiClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val QUEUE_KEY = "notifications:queue"
        const val MAX_PER_SECOND = 25
    }

    fun enqueue(chatId: Long, text: String, buttonText: String? = null, buttonUrl: String? = null) {
        val notification = QueuedNotification(chatId, text, buttonText, buttonUrl)
        redisTemplate.opsForList().leftPush(QUEUE_KEY, notification)
    }

    fun queueSize(): Long = redisTemplate.opsForList().size(QUEUE_KEY) ?: 0

    /**
     * Process up to 25 notifications per second from the queue.
     * Runs every 1000ms; sends at most [MAX_PER_SECOND] messages per tick.
     */
    @Scheduled(fixedRate = 1000)
    fun processQueue() {
        var count = 0
        while (count < MAX_PER_SECOND) {
            val raw = redisTemplate.opsForList().rightPop(QUEUE_KEY) ?: break
            val notification = raw as? QueuedNotification
            if (notification == null) {
                log.warn("Could not deserialize queued notification: {}", raw)
                count++
                continue
            }
            val button = if (notification.buttonText != null && notification.buttonUrl != null) {
                InlineKeyboardMarkup(
                    inlineKeyboard = listOf(
                        listOf(InlineKeyboardButton(text = notification.buttonText, url = notification.buttonUrl))
                    )
                )
            } else null
            telegramApiClient.sendMessage(notification.chatId, notification.text, button)
            count++
        }
        if (count > 0) {
            log.debug("Processed {} notification(s) from queue", count)
        }
    }
}
