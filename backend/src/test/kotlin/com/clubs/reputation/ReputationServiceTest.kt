package com.clubs.reputation

import com.clubs.config.NotFoundException
import com.clubs.event.EventDto
import com.clubs.event.EventRepository
import com.clubs.event.EventResponseDto
import com.clubs.event.EventResponseRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.OffsetDateTime
import java.util.UUID

class ReputationServiceTest {

    private lateinit var eventRepository: EventRepository
    private lateinit var eventResponseRepository: EventResponseRepository
    private lateinit var reputationRepository: ReputationRepository
    private lateinit var service: ReputationService

    private val eventId = UUID.randomUUID()
    private val clubId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val now = OffsetDateTime.now()

    @BeforeEach
    fun setUp() {
        eventRepository = mock()
        eventResponseRepository = mock()
        reputationRepository = mock()
        service = ReputationService(eventRepository, eventResponseRepository, reputationRepository)
    }

    private fun makeEvent() = EventDto(
        id = eventId, clubId = clubId, title = "Test", description = null, location = null,
        eventDatetime = now, participantLimit = 10, confirmedCount = 0,
        votingOpensDaysBefore = 3, status = "completed", stage2Triggered = true,
        attendanceFinalized = true, attendanceFinalizedAt = now,
        createdAt = now, updatedAt = now
    )

    private fun makeResponse(
        userId: UUID = this.userId,
        stage1Status: String?,
        finalStatus: String?
    ) = EventResponseDto(
        id = UUID.randomUUID(), eventId = eventId, userId = userId,
        stage1Status = stage1Status, finalStatus = finalStatus,
        waitlistPosition = null, attended = null,
        respondedAt = now, confirmedAt = null, createdAt = now, updatedAt = now
    )

    @Test
    fun `throws NotFoundException when event not found`() {
        whenever(eventRepository.findById(eventId)).thenReturn(null)

        assertThrows<NotFoundException> { service.calculateAndUpdate(eventId) }
        verifyNoInteractions(reputationRepository)
    }

    @Test
    fun `going attended gives +100 reliability, totalConfirmed+1, totalAttended+1`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEvent(eventId))
            .thenReturn(listOf(makeResponse(stage1Status = "going", finalStatus = "attended")))

        service.calculateAndUpdate(eventId)

        verify(reputationRepository).upsert(
            userId = userId, clubId = clubId,
            reliabilityDelta = 100, totalConfirmedDelta = 1, totalAttendedDelta = 1, spontaneityDelta = 0
        )
    }

    @Test
    fun `going absent gives -50 reliability, totalConfirmed+1, totalAttended+0`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEvent(eventId))
            .thenReturn(listOf(makeResponse(stage1Status = "going", finalStatus = "absent")))

        service.calculateAndUpdate(eventId)

        verify(reputationRepository).upsert(
            userId = userId, clubId = clubId,
            reliabilityDelta = -50, totalConfirmedDelta = 1, totalAttendedDelta = 0, spontaneityDelta = 0
        )
    }

    @Test
    fun `going declined gives 0 points but totalConfirmed+1`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEvent(eventId))
            .thenReturn(listOf(makeResponse(stage1Status = "going", finalStatus = "declined")))

        service.calculateAndUpdate(eventId)

        verify(reputationRepository).upsert(
            userId = userId, clubId = clubId,
            reliabilityDelta = 0, totalConfirmedDelta = 1, totalAttendedDelta = 0, spontaneityDelta = 0
        )
    }

    @Test
    fun `maybe attended gives +30 reliability and spontaneity+1`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEvent(eventId))
            .thenReturn(listOf(makeResponse(stage1Status = "maybe", finalStatus = "attended")))

        service.calculateAndUpdate(eventId)

        verify(reputationRepository).upsert(
            userId = userId, clubId = clubId,
            reliabilityDelta = 30, totalConfirmedDelta = 1, totalAttendedDelta = 1, spontaneityDelta = 1
        )
    }

    @Test
    fun `maybe absent gives 0 points, totalConfirmed+1`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEvent(eventId))
            .thenReturn(listOf(makeResponse(stage1Status = "maybe", finalStatus = "absent")))

        service.calculateAndUpdate(eventId)

        verify(reputationRepository).upsert(
            userId = userId, clubId = clubId,
            reliabilityDelta = 0, totalConfirmedDelta = 1, totalAttendedDelta = 0, spontaneityDelta = 0
        )
    }

    @Test
    fun `not_going or null finalStatus gives no reputation update (Molchun)`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEvent(eventId)).thenReturn(listOf(
            makeResponse(stage1Status = "not_going", finalStatus = null),
            makeResponse(stage1Status = "going", finalStatus = "waitlisted"),
            makeResponse(stage1Status = null, finalStatus = null)
        ))

        service.calculateAndUpdate(eventId)

        verifyNoInteractions(reputationRepository)
    }

    @Test
    fun `multiple participants in same event processed independently per club`() {
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEvent(eventId)).thenReturn(listOf(
            makeResponse(userId = user1, stage1Status = "going", finalStatus = "attended"),
            makeResponse(userId = user2, stage1Status = "going", finalStatus = "absent")
        ))

        service.calculateAndUpdate(eventId)

        verify(reputationRepository).upsert(user1, clubId, 100, 1, 1, 0)
        verify(reputationRepository).upsert(user2, clubId, -50, 1, 0, 0)
        verifyNoMoreInteractions(reputationRepository)
    }

    @Test
    fun `empty event responses produce no reputation updates`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEvent(eventId)).thenReturn(emptyList())

        service.calculateAndUpdate(eventId)

        verifyNoInteractions(reputationRepository)
    }

    @Test
    fun `getClubReputation delegates to repository`() {
        whenever(reputationRepository.findByClub(clubId)).thenReturn(emptyList())
        val result = service.getClubReputation(clubId)
        assertTrue(result.isEmpty())
        verify(reputationRepository).findByClub(clubId)
    }

    @Test
    fun `getAllUserReputations delegates to repository`() {
        whenever(reputationRepository.findAllByUser(userId)).thenReturn(emptyList())
        val result = service.getAllUserReputations(userId)
        assertTrue(result.isEmpty())
        verify(reputationRepository).findAllByUser(userId)
    }
}
