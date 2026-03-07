package com.clubs.bot

import com.clubs.club.ClubDto
import com.clubs.club.ClubRepository
import com.clubs.event.EventDto
import com.clubs.event.EventRepository
import com.clubs.event.EventResponseRepository
import com.clubs.event.VoteCountsDto
import com.clubs.payment.TelegramStarsPaymentService
import com.clubs.reputation.ReputationDto
import com.clubs.reputation.ReputationService
import com.clubs.user.UserDto
import com.clubs.user.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TelegramBotServiceTest {

    @Mock lateinit var telegramApiClient: TelegramApiClient
    @Mock lateinit var clubRepository: ClubRepository
    @Mock lateinit var eventRepository: EventRepository
    @Mock lateinit var eventResponseRepository: EventResponseRepository
    @Mock lateinit var reputationService: ReputationService
    @Mock lateinit var userService: UserService
    @Mock lateinit var telegramStarsPaymentService: TelegramStarsPaymentService

    private lateinit var telegramBotService: TelegramBotService

    private val groupChatId = -100123456L
    private val userTelegramId = 42L
    private val clubId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        telegramBotService = TelegramBotService(
            telegramApiClient = telegramApiClient,
            botUsername = "clubsapp",
            miniAppUrl = "https://t.me/clubsapp",
            clubRepository = clubRepository,
            eventRepository = eventRepository,
            eventResponseRepository = eventResponseRepository,
            reputationService = reputationService,
            userService = userService,
            telegramStarsPaymentService = telegramStarsPaymentService
        )
    }

    // ---- Existing /start and /help tests ----

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
    fun `processUpdate with myChatMember added sends linking instructions`() {
        val groupId = -100123L
        val update = TelegramUpdate(
            updateId = 6L,
            myChatMember = TelegramChatMemberUpdated(
                chat = TelegramChat(id = groupId, type = "group", title = "Test Group"),
                from = TelegramUser(id = 123L, firstName = "Ivan"),
                newChatMember = TelegramChatMember(
                    user = TelegramUser(id = 999L, firstName = "ClubsBot"),
                    status = "member"
                )
            )
        )
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(groupId), any(), anyOrNull())
    }

    @Test
    fun `processUpdate with myChatMember removed does not send message`() {
        val update = TelegramUpdate(
            updateId = 7L,
            myChatMember = TelegramChatMemberUpdated(
                chat = TelegramChat(id = -100123L, type = "group", title = "Test Group"),
                from = TelegramUser(id = 123L, firstName = "Ivan"),
                newChatMember = TelegramChatMember(
                    user = TelegramUser(id = 999L, firstName = "ClubsBot"),
                    status = "left"
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

    // ---- /кто_идет tests ----

    @Test
    fun `кто_идет returns not-linked message when chat has no club`() {
        whenever(clubRepository.findByTelegramGroupId(groupChatId)).thenReturn(null)
        val update = makeGroupUpdate("/кто_идет")
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(groupChatId), any(), anyOrNull())
    }

    @Test
    fun `кто_идет returns no events message when club has no upcoming events`() {
        whenever(clubRepository.findByTelegramGroupId(groupChatId)).thenReturn(makeClub())
        whenever(eventRepository.findUpcomingByClub(clubId)).thenReturn(emptyList())
        val update = makeGroupUpdate("/кто_идет")
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(groupChatId), any(), anyOrNull())
    }

    @Test
    fun `кто_идет returns event stats when club has upcoming event`() {
        val club = makeClub()
        val event = makeEvent()
        whenever(clubRepository.findByTelegramGroupId(groupChatId)).thenReturn(club)
        whenever(eventRepository.findUpcomingByClub(clubId)).thenReturn(listOf(event))
        whenever(eventResponseRepository.countByStatus(eventId)).thenReturn(VoteCountsDto(3, 2, 1))
        val update = makeGroupUpdate("/кто_идет")
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(groupChatId), any(), any())
    }

    // ---- /мой_рейтинг tests ----

    @Test
    fun `мой_рейтинг returns not-linked message when chat has no club`() {
        whenever(clubRepository.findByTelegramGroupId(groupChatId)).thenReturn(null)
        val update = makeGroupUpdate("/мой_рейтинг", fromId = userTelegramId)
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(groupChatId), any(), anyOrNull())
    }

    @Test
    fun `мой_рейтинг returns register message when user not found`() {
        whenever(clubRepository.findByTelegramGroupId(groupChatId)).thenReturn(makeClub())
        whenever(userService.findByTelegramId(userTelegramId)).thenReturn(null)
        val update = makeGroupUpdate("/мой_рейтинг", fromId = userTelegramId)
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(groupChatId), any(), anyOrNull())
    }

    @Test
    fun `мой_рейтинг returns no reputation message when user has no history`() {
        whenever(clubRepository.findByTelegramGroupId(groupChatId)).thenReturn(makeClub())
        whenever(userService.findByTelegramId(userTelegramId)).thenReturn(makeUser())
        whenever(reputationService.getUserClubReputation(userId, clubId)).thenReturn(null)
        val update = makeGroupUpdate("/мой_рейтинг", fromId = userTelegramId)
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(groupChatId), any(), anyOrNull())
    }

    @Test
    fun `мой_рейтинг returns reputation when user has history`() {
        whenever(clubRepository.findByTelegramGroupId(groupChatId)).thenReturn(makeClub())
        whenever(userService.findByTelegramId(userTelegramId)).thenReturn(makeUser())
        whenever(reputationService.getUserClubReputation(userId, clubId)).thenReturn(makeReputation())
        val update = makeGroupUpdate("/мой_рейтинг", fromId = userTelegramId)
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(groupChatId), any(), anyOrNull())
    }

    // ---- /события tests ----

    @Test
    fun `события returns not-linked message when chat has no club`() {
        whenever(clubRepository.findByTelegramGroupId(groupChatId)).thenReturn(null)
        val update = makeGroupUpdate("/события")
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(groupChatId), any(), anyOrNull())
    }

    @Test
    fun `события returns no events message when club has no upcoming events`() {
        whenever(clubRepository.findByTelegramGroupId(groupChatId)).thenReturn(makeClub())
        whenever(eventRepository.findUpcomingByClub(clubId)).thenReturn(emptyList())
        val update = makeGroupUpdate("/события")
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(groupChatId), any(), anyOrNull())
    }

    @Test
    fun `события returns event list with buttons when events exist`() {
        whenever(clubRepository.findByTelegramGroupId(groupChatId)).thenReturn(makeClub())
        whenever(eventRepository.findUpcomingByClub(clubId)).thenReturn(listOf(makeEvent(), makeEvent(title = "Event 2")))
        val update = makeGroupUpdate("/события")
        telegramBotService.processUpdate(update)
        verify(telegramApiClient).sendMessage(eq(groupChatId), any(), any())
    }

    // ---- Helpers ----

    private fun makeUpdate(text: String, chatId: Long = 123L, updateId: Long = 1L) = TelegramUpdate(
        updateId = updateId,
        message = TelegramMessage(
            messageId = updateId,
            from = TelegramUser(id = chatId, firstName = "Ivan"),
            chat = TelegramChat(id = chatId, type = "private"),
            text = text
        )
    )

    private fun makeGroupUpdate(text: String, fromId: Long = 111L) = TelegramUpdate(
        updateId = 1L,
        message = TelegramMessage(
            messageId = 1L,
            from = TelegramUser(id = fromId, firstName = "Ivan"),
            chat = TelegramChat(id = groupChatId, type = "group", title = "Test Club Group"),
            text = text
        )
    )

    private fun makeClub() = ClubDto(
        id = clubId,
        ownerId = UUID.randomUUID(),
        name = "Test Club",
        description = null,
        city = "Moscow",
        category = "sport",
        accessType = "open",
        memberLimit = 20,
        subscriptionPrice = 100,
        avatarUrl = null,
        coverUrl = null,
        rules = null,
        applicationQuestion = null,
        telegramGroupId = groupChatId,
        activityRating = 1.0,
        confirmedCount = 5,
        isActive = true,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now()
    )

    private fun makeEvent(title: String = "Test Event") = EventDto(
        id = eventId,
        clubId = clubId,
        title = title,
        description = null,
        location = null,
        eventDatetime = OffsetDateTime.now().plusDays(3),
        participantLimit = 20,
        confirmedCount = 5,
        votingOpensDaysBefore = 3,
        status = "stage_1",
        stage2Triggered = false,
        attendanceFinalized = false,
        attendanceFinalizedAt = null,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now()
    )

    private fun makeUser() = UserDto(
        id = userId,
        telegramId = userTelegramId,
        username = "ivan",
        firstName = "Ivan",
        lastName = null,
        avatarUrl = null,
        city = null,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now()
    )

    private fun makeReputation() = ReputationDto(
        id = UUID.randomUUID(),
        userId = userId,
        clubId = clubId,
        reliabilityIndex = 150,
        promiseFulfillmentPct = 85.0,
        spontaneityCount = 2,
        totalConfirmed = 5,
        totalAttended = 4,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now()
    )
}
