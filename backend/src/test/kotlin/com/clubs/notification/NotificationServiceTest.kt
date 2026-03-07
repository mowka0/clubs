package com.clubs.notification

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.UUID

class NotificationServiceTest {

    private lateinit var notificationQueueService: NotificationQueueService
    private lateinit var notificationService: NotificationService

    private val miniAppUrl = "https://t.me/testapp"
    private val telegramId = 12345L
    private val clubId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        notificationQueueService = mock()
        notificationService = NotificationService(notificationQueueService, miniAppUrl)
    }

    @Test
    fun `sendPersonalNotification enqueues to queue`() {
        notificationService.sendPersonalNotification(telegramId, "Hello!")

        verify(notificationQueueService).enqueue(telegramId, "Hello!", null, null)
    }

    @Test
    fun `sendPersonalNotification with button enqueues with button data`() {
        notificationService.sendPersonalNotification(telegramId, "Hello!", "Open", "https://example.com")

        verify(notificationQueueService).enqueue(telegramId, "Hello!", "Open", "https://example.com")
    }

    @Test
    fun `sendGroupNotification enqueues to queue with correct chatId`() {
        val chatId = -100123456L
        notificationService.sendGroupNotification(chatId, "Group message")

        verify(notificationQueueService).enqueue(chatId, "Group message", null, null)
    }

    @Test
    fun `notifyApplicationApproved sends notification with club deep-link`() {
        notificationService.notifyApplicationApproved(telegramId, "Футбол", clubId)

        verify(notificationQueueService).enqueue(
            eq(telegramId),
            argThat { contains("Футбол") && contains("одобрена") },
            eq("Открыть клуб"),
            eq("$miniAppUrl?startapp=club_$clubId")
        )
    }

    @Test
    fun `notifyApplicationRejected sends notification with reason`() {
        notificationService.notifyApplicationRejected(telegramId, "Спорт", "Нет мест")

        verify(notificationQueueService).enqueue(
            eq(telegramId),
            argThat { contains("Спорт") && contains("отклонена") && contains("Нет мест") },
            isNull(),
            isNull()
        )
    }

    @Test
    fun `notifyApplicationRejected without reason sends generic message`() {
        notificationService.notifyApplicationRejected(telegramId, "Спорт", null)

        verify(notificationQueueService).enqueue(
            eq(telegramId),
            argThat { contains("Спорт") && contains("отклонена") && !contains("Причина") },
            isNull(),
            isNull()
        )
    }

    @Test
    fun `notifyStage2Started sends notification with event deep-link`() {
        notificationService.notifyStage2Started(telegramId, "Турнир", eventId)

        verify(notificationQueueService).enqueue(
            eq(telegramId),
            argThat { contains("Турнир") && contains("Этап 2") },
            eq("Подтвердить участие"),
            eq("$miniAppUrl?startapp=event_$eventId")
        )
    }

    @Test
    fun `notifyWaitlisted sends notification with position`() {
        notificationService.notifyWaitlisted(telegramId, "Марафон", eventId, 3)

        verify(notificationQueueService).enqueue(
            eq(telegramId),
            argThat { contains("Марафон") && contains("3") && contains("ожидания") },
            any(),
            any()
        )
    }

    @Test
    fun `notifySlotFreed sends notification with event deep-link`() {
        notificationService.notifySlotFreed(telegramId, "Концерт", eventId)

        verify(notificationQueueService).enqueue(
            eq(telegramId),
            argThat { contains("Концерт") && contains("освободилось") },
            any(),
            eq("$miniAppUrl?startapp=event_$eventId")
        )
    }

    @Test
    fun `notifyMarkedAbsent sends notification with dispute button`() {
        notificationService.notifyMarkedAbsent(telegramId, "Кино", eventId)

        verify(notificationQueueService).enqueue(
            eq(telegramId),
            argThat { contains("Кино") && contains("не пришедшего") },
            eq("Оспорить"),
            any()
        )
    }

    @Test
    fun `notifySubscriptionExpiringSoon sends notification with days left`() {
        notificationService.notifySubscriptionExpiringSoon(telegramId, "Шахматы", clubId, 2)

        verify(notificationQueueService).enqueue(
            eq(telegramId),
            argThat { contains("Шахматы") && contains("2 дн.") },
            any(),
            eq("$miniAppUrl?startapp=club_$clubId")
        )
    }

    @Test
    fun `sendPersonalNotification swallows queue exception gracefully`() {
        whenever(notificationQueueService.enqueue(any(), any(), anyOrNull(), anyOrNull()))
            .thenThrow(RuntimeException("Redis unavailable"))

        // Should not throw
        notificationService.sendPersonalNotification(telegramId, "Test")
    }

    @Test
    fun `buildDeepLink generates correct URL`() {
        val link = notificationService.buildDeepLink("event_abc123")
        assert(link == "$miniAppUrl?startapp=event_abc123")
    }
}
