package com.clubs.bot

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class)
class TelegramBotServiceTest {

    @Mock
    lateinit var telegramApiClient: TelegramApiClient

    private lateinit var telegramBotService: TelegramBotService

    @BeforeEach
    fun setUp() {
        telegramBotService = TelegramBotService(
            telegramApiClient = telegramApiClient,
            botUsername = "clubsapp",
            miniAppUrl = "https://t.me/clubsapp"
        )
    }

    @Test
    fun `processUpdate with start command sends welcome message`() {
        val update = makeUpdate(text = "/start")
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(123L), any(), any())
    }

    @Test
    fun `processUpdate with help command sends welcome message`() {
        val update = makeUpdate(text = "/help")
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(123L), any(), any())
    }

    @Test
    fun `processUpdate with start command containing botname sends welcome message`() {
        val update = makeUpdate(text = "/start@clubsapp")
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(123L), any(), any())
    }

    @Test
    fun `processUpdate with unknown command does not crash and does not send message`() {
        val update = makeUpdate(text = "/unknown_command")
        telegramBotService.processUpdate(update)
        verify(telegramApiClient, never()).sendMessage(any(), any(), any())
    }

    @Test
    fun `processUpdate with null message does not crash`() {
        val update = TelegramUpdate(updateId = 3L)
        telegramBotService.processUpdate(update)
        verify(telegramApiClient, never()).sendMessage(any(), any(), any())
    }

    @Test
    fun `processUpdate with message without text does not crash`() {
        val update = TelegramUpdate(
            updateId = 4L,
            message = TelegramMessage(
                messageId = 4L,
                from = TelegramUser(id = 123L, firstName = "Ivan"),
                chat = TelegramChat(id = 123L, type = "private"),
                text = null
            )
        )
        telegramBotService.processUpdate(update)
        verify(telegramApiClient, never()).sendMessage(any(), any(), any())
    }

    @Test
    fun `processUpdate with callback query does not crash`() {
        val update = TelegramUpdate(
            updateId = 5L,
            callbackQuery = TelegramCallbackQuery(
                id = "query_1",
                from = TelegramUser(id = 123L, firstName = "Ivan"),
                data = "some_data"
            )
        )
        telegramBotService.processUpdate(update)
        verify(telegramApiClient, never()).sendMessage(any(), any(), any())
    }

    @Test
    fun `processUpdate with myChatMember does not crash`() {
        val update = TelegramUpdate(
            updateId = 6L,
            myChatMember = TelegramChatMemberUpdated(
                chat = TelegramChat(id = -100123L, type = "group", title = "Test Group"),
                from = TelegramUser(id = 123L, firstName = "Ivan"),
                newChatMember = TelegramChatMember(
                    user = TelegramUser(id = 999L, firstName = "ClubsBot"),
                    status = "member"
                )
            )
        )
        telegramBotService.processUpdate(update)
        verify(telegramApiClient, never()).sendMessage(any(), any(), any())
    }

    @Test
    fun `buildDeepLink formats correctly`() {
        val link = telegramBotService.buildDeepLink("event_123")
        assert(link == "https://t.me/clubsapp?startapp=event_123") {
            "Expected deep link with startapp param, got: $link"
        }
    }

    private fun makeUpdate(text: String, chatId: Long = 123L, updateId: Long = 1L) = TelegramUpdate(
        updateId = updateId,
        message = TelegramMessage(
            messageId = updateId,
            from = TelegramUser(id = chatId, firstName = "Ivan"),
            chat = TelegramChat(id = chatId, type = "private"),
            text = text
        )
    )
}
