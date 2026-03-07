package com.clubs.event

import com.clubs.config.NotFoundException
import com.clubs.config.ValidationException
import com.clubs.generated.jooq.enums.FinalStatus
import com.clubs.notification.NotificationService
import com.clubs.user.UserService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import com.clubs.membership.MembershipService
import java.time.OffsetDateTime
import java.util.UUID

class EventStage2ConfirmDeclineTest {

    private lateinit var eventResponseRepository: EventResponseRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var membershipService: MembershipService
    private lateinit var userService: UserService
    private lateinit var notificationService: NotificationService
    private lateinit var service: EventResponseService

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val clubId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        eventResponseRepository = mock()
        eventRepository = mock()
        membershipService = mock()
        userService = mock()
        notificationService = mock()
        whenever(userService.findById(any())).thenReturn(null)
        service = EventResponseService(eventResponseRepository, eventRepository, membershipService, userService, notificationService)
    }

    private fun makeEvent(status: String = "stage_2", limit: Int = 3, confirmed: Int = 0): EventDto {
        val now = OffsetDateTime.now()
        return EventDto(
            id = eventId,
            clubId = clubId,
            title = "Test Event",
            description = null,
            location = null,
            eventDatetime = now.plusHours(23),
            participantLimit = limit,
            confirmedCount = confirmed,
            votingOpensDaysBefore = 3,
            status = status,
            stage2Triggered = true,
            attendanceFinalized = false,
            attendanceFinalizedAt = null,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun makeResponse(
        uid: UUID = userId,
        stage1Status: String = "going",
        finalStatus: String? = null,
        waitlistPosition: Int? = null
    ): EventResponseDto {
        val now = OffsetDateTime.now()
        return EventResponseDto(
            id = UUID.randomUUID(),
            eventId = eventId,
            userId = uid,
            stage1Status = stage1Status,
            finalStatus = finalStatus,
            waitlistPosition = waitlistPosition,
            attended = null,
            respondedAt = now,
            confirmedAt = null,
            createdAt = now,
            updatedAt = now
        )
    }

    // --- confirm tests ---

    @Test
    fun `confirm throws NotFoundException when event does not exist`() {
        whenever(eventRepository.findById(eventId)).thenReturn(null)

        assertThrows<NotFoundException> { service.confirm(userId, eventId) }
    }

    @Test
    fun `confirm throws ValidationException when event is not in stage_2`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent(status = "stage_1"))

        assertThrows<ValidationException> { service.confirm(userId, eventId) }
    }

    @Test
    fun `confirm throws NotFoundException when user has no vote`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId)).thenReturn(null)

        assertThrows<NotFoundException> { service.confirm(userId, eventId) }
    }

    @Test
    fun `confirm throws ValidationException when user voted not_going`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId))
            .thenReturn(makeResponse(stage1Status = "not_going"))

        assertThrows<ValidationException> { service.confirm(userId, eventId) }
    }

    @Test
    fun `confirm returns confirmed when slot is available`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent(limit = 3, confirmed = 0))
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId))
            .thenReturn(makeResponse(stage1Status = "going"))
        whenever(eventRepository.atomicIncrementConfirmedCount(eventId, 3)).thenReturn(1)

        val result = service.confirm(userId, eventId)

        assertEquals("confirmed", result.finalStatus)
        assertNull(result.positionInWaitlist)
        verify(eventResponseRepository).updateFinalStatus(eventId, userId, FinalStatus.confirmed)
    }

    @Test
    fun `confirm returns waitlisted when no slot available`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent(limit = 3, confirmed = 3))
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId))
            .thenReturn(makeResponse(stage1Status = "going"))
        whenever(eventRepository.atomicIncrementConfirmedCount(eventId, 3)).thenReturn(null)
        whenever(eventResponseRepository.findWaitlistedByEvent(eventId)).thenReturn(emptyList())

        val result = service.confirm(userId, eventId)

        assertEquals("waitlisted", result.finalStatus)
        assertEquals(1, result.positionInWaitlist)
        verify(eventResponseRepository).updateFinalStatus(eventId, userId, FinalStatus.waitlisted, waitlistPosition = 1)
    }

    @Test
    fun `confirm assigns correct waitlist position when others already waitlisted`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent(limit = 3, confirmed = 3))
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId))
            .thenReturn(makeResponse(stage1Status = "going"))
        whenever(eventRepository.atomicIncrementConfirmedCount(eventId, 3)).thenReturn(null)
        whenever(eventResponseRepository.findWaitlistedByEvent(eventId)).thenReturn(
            listOf(makeResponse(waitlistPosition = 1), makeResponse(waitlistPosition = 2))
        )

        val result = service.confirm(userId, eventId)

        assertEquals("waitlisted", result.finalStatus)
        assertEquals(3, result.positionInWaitlist)
    }

    @Test
    fun `confirm is idempotent when already confirmed`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId))
            .thenReturn(makeResponse(finalStatus = "confirmed"))

        val result = service.confirm(userId, eventId)

        assertEquals("confirmed", result.finalStatus)
        verify(eventRepository, never()).atomicIncrementConfirmedCount(any(), any())
    }

    @Test
    fun `confirm works for maybe voter in Scenario B`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent(limit = 5, confirmed = 2))
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId))
            .thenReturn(makeResponse(stage1Status = "maybe"))
        whenever(eventRepository.atomicIncrementConfirmedCount(eventId, 5)).thenReturn(3)

        val result = service.confirm(userId, eventId)

        assertEquals("confirmed", result.finalStatus)
        verify(eventResponseRepository).updateFinalStatus(eventId, userId, FinalStatus.confirmed)
    }

    // --- decline tests ---

    @Test
    fun `decline throws NotFoundException when event does not exist`() {
        whenever(eventRepository.findById(eventId)).thenReturn(null)

        assertThrows<NotFoundException> { service.decline(userId, eventId) }
    }

    @Test
    fun `decline throws ValidationException when event is not in stage_2`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent(status = "upcoming"))

        assertThrows<ValidationException> { service.decline(userId, eventId) }
    }

    @Test
    fun `decline throws NotFoundException when user has no vote`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId)).thenReturn(null)

        assertThrows<NotFoundException> { service.decline(userId, eventId) }
    }

    @Test
    fun `decline sets status to declined and returns declined`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId))
            .thenReturn(makeResponse(finalStatus = "waitlisted"))
        whenever(eventResponseRepository.findWaitlistedByEvent(eventId)).thenReturn(emptyList())

        val result = service.decline(userId, eventId)

        assertEquals("declined", result.finalStatus)
        verify(eventResponseRepository).updateFinalStatus(eventId, userId, FinalStatus.declined)
    }

    @Test
    fun `decline does not decrement confirmed count when user was not confirmed`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId))
            .thenReturn(makeResponse(finalStatus = "waitlisted"))
        whenever(eventResponseRepository.findWaitlistedByEvent(eventId)).thenReturn(emptyList())

        service.decline(userId, eventId)

        verify(eventRepository, never()).decrementConfirmedCount(any())
    }

    @Test
    fun `decline promotes first waitlisted when confirmed user declines`() {
        val waitlistedUser = makeResponse(uid = otherUserId, finalStatus = "waitlisted", waitlistPosition = 1)

        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent(limit = 3, confirmed = 3))
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId))
            .thenReturn(makeResponse(finalStatus = "confirmed"))
        whenever(eventResponseRepository.findWaitlistedByEvent(eventId)).thenReturn(listOf(waitlistedUser))
        whenever(eventRepository.atomicIncrementConfirmedCount(eventId, 3)).thenReturn(3)

        service.decline(userId, eventId)

        verify(eventRepository).decrementConfirmedCount(eventId)
        verify(eventResponseRepository).updateFinalStatus(eventId, otherUserId, FinalStatus.confirmed)
    }

    @Test
    fun `decline does not promote waitlisted if no vacancy after atomic increment`() {
        val waitlistedUser = makeResponse(uid = otherUserId, finalStatus = "waitlisted", waitlistPosition = 1)

        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent(limit = 3, confirmed = 3))
        whenever(eventResponseRepository.findByEventAndUser(eventId, userId))
            .thenReturn(makeResponse(finalStatus = "confirmed"))
        whenever(eventResponseRepository.findWaitlistedByEvent(eventId)).thenReturn(listOf(waitlistedUser))
        whenever(eventRepository.atomicIncrementConfirmedCount(eventId, 3)).thenReturn(null)

        service.decline(userId, eventId)

        verify(eventRepository).decrementConfirmedCount(eventId)
        // Should NOT update waitlisted user to confirmed
        verify(eventResponseRepository, never()).updateFinalStatus(eventId, otherUserId, FinalStatus.confirmed)
    }
}
