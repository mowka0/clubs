package com.clubs.event

import com.clubs.config.NotFoundException
import com.clubs.config.ValidationException
import com.clubs.generated.jooq.enums.VoteStatus
import com.clubs.membership.MembershipService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.access.AccessDeniedException
import java.time.OffsetDateTime
import java.util.UUID

class EventResponseServiceTest {

    private lateinit var eventResponseRepository: EventResponseRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var membershipService: MembershipService
    private lateinit var service: EventResponseService

    private val userId = UUID.randomUUID()
    private val clubId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        eventResponseRepository = mock()
        eventRepository = mock()
        membershipService = mock()
        service = EventResponseService(eventResponseRepository, eventRepository, membershipService)
    }

    private fun makeEvent(status: String = "stage_1"): EventDto {
        val now = OffsetDateTime.now()
        return EventDto(
            id = eventId,
            clubId = clubId,
            title = "Test Event",
            description = null,
            location = null,
            eventDatetime = now.plusDays(1),
            participantLimit = 20,
            confirmedCount = 0,
            votingOpensDaysBefore = 3,
            status = status,
            stage2Triggered = false,
            attendanceFinalized = false,
            attendanceFinalizedAt = null,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun makeResponse(stage1Status: String = "going"): EventResponseDto {
        val now = OffsetDateTime.now()
        return EventResponseDto(
            id = UUID.randomUUID(),
            eventId = eventId,
            userId = userId,
            stage1Status = stage1Status,
            finalStatus = null,
            waitlistPosition = null,
            attended = null,
            respondedAt = now,
            confirmedAt = null,
            createdAt = now,
            updatedAt = now
        )
    }

    @Test
    fun `vote throws NotFoundException when event does not exist`() {
        whenever(eventRepository.findById(eventId)).thenReturn(null)

        assertThrows<NotFoundException> { service.vote(userId, eventId, "going") }
    }

    @Test
    fun `vote throws ValidationException when event is not in stage_1`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent(status = "upcoming"))

        assertThrows<ValidationException> { service.vote(userId, eventId, "going") }
    }

    @Test
    fun `vote throws AccessDeniedException when user is not active member`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(membershipService.isActiveMember(userId, clubId)).thenReturn(false)

        assertThrows<AccessDeniedException> { service.vote(userId, eventId, "going") }
    }

    @Test
    fun `vote throws ValidationException for invalid status`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(membershipService.isActiveMember(userId, clubId)).thenReturn(true)

        assertThrows<ValidationException> { service.vote(userId, eventId, "invalid_status") }
    }

    @Test
    fun `vote creates response when valid`() {
        val response = makeResponse("going")
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(membershipService.isActiveMember(userId, clubId)).thenReturn(true)
        whenever(eventResponseRepository.createOrUpdate(eventId, userId, VoteStatus.going)).thenReturn(response)

        val result = service.vote(userId, eventId, "going")

        assertEquals(response, result)
        verify(eventResponseRepository).createOrUpdate(eventId, userId, VoteStatus.going)
    }

    @Test
    fun `vote updates response when changing from going to maybe`() {
        val response = makeResponse("maybe")
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(membershipService.isActiveMember(userId, clubId)).thenReturn(true)
        whenever(eventResponseRepository.createOrUpdate(eventId, userId, VoteStatus.maybe)).thenReturn(response)

        val result = service.vote(userId, eventId, "maybe")

        assertEquals("maybe", result.stage1Status)
        verify(eventResponseRepository).createOrUpdate(eventId, userId, VoteStatus.maybe)
    }

    @Test
    fun `countByStatus returns correct vote counts`() {
        val counts = VoteCountsDto(going = 3, maybe = 2, notGoing = 1)
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.countByStatus(eventId)).thenReturn(counts)

        val result = service.countByStatus(eventId)

        assertEquals(3, result.going)
        assertEquals(2, result.maybe)
        assertEquals(1, result.notGoing)
    }

    @Test
    fun `countByStatus throws NotFoundException when event does not exist`() {
        whenever(eventRepository.findById(eventId)).thenReturn(null)

        assertThrows<NotFoundException> { service.countByStatus(eventId) }
    }

    @Test
    fun `getResponses throws AccessDeniedException for non-member`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(membershipService.isActiveMember(userId, clubId)).thenReturn(false)

        assertThrows<AccessDeniedException> { service.getResponses(eventId, userId) }
    }

    @Test
    fun `getResponses returns list for active member`() {
        val responses = listOf(makeResponse("going"), makeResponse("maybe"))
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(membershipService.isActiveMember(userId, clubId)).thenReturn(true)
        whenever(eventResponseRepository.findByEvent(eventId)).thenReturn(responses)

        val result = service.getResponses(eventId, userId)

        assertEquals(2, result.size)
    }
}
