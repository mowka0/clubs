package com.clubs.event

import com.clubs.generated.jooq.enums.FinalStatus
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.OffsetDateTime
import java.util.UUID

class EventStage2SchedulerTest {

    private lateinit var dsl: DSLContext
    private lateinit var eventRepository: EventRepository
    private lateinit var eventResponseRepository: EventResponseRepository
    private lateinit var scheduler: EventScheduler

    private val clubId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        dsl = mock()
        eventRepository = mock()
        eventResponseRepository = mock()
        scheduler = EventScheduler(dsl, eventRepository, eventResponseRepository)
    }

    private fun makeEvent(limit: Int): EventDto = EventDto(
        id = UUID.randomUUID(),
        clubId = clubId,
        title = "Test Event",
        description = null,
        location = null,
        eventDatetime = OffsetDateTime.now().plusHours(12),
        participantLimit = limit,
        confirmedCount = 0,
        votingOpensDaysBefore = 3,
        status = "stage_1",
        stage2Triggered = false,
        attendanceFinalized = false,
        attendanceFinalizedAt = null,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now()
    )

    private fun makeResponse(eventId: UUID, userId: UUID = UUID.randomUUID()): EventResponseDto = EventResponseDto(
        id = UUID.randomUUID(),
        eventId = eventId,
        userId = userId,
        stage1Status = "going",
        finalStatus = null,
        waitlistPosition = null,
        attended = null,
        respondedAt = OffsetDateTime.now(),
        confirmedAt = null,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now()
    )

    @Test
    fun `no eligible events - does nothing`() {
        whenever(eventRepository.findEventsReadyForStage2()).thenReturn(emptyList())

        scheduler.triggerStage2ForEligibleEvents()

        verify(eventRepository, never()).markStage2Triggered(any(), any())
        verify(eventResponseRepository, never()).updateFinalStatus(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `scenario A - going exceeds limit, FIFO confirmed and waitlisted`() {
        val event = makeEvent(limit = 2)
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        val user3 = UUID.randomUUID()
        val goingResponses = listOf(
            makeResponse(event.id, user1),
            makeResponse(event.id, user2),
            makeResponse(event.id, user3)
        )

        whenever(eventRepository.findEventsReadyForStage2()).thenReturn(listOf(event))
        whenever(eventResponseRepository.findGoingByEvent(event.id)).thenReturn(goingResponses)

        scheduler.triggerStage2ForEligibleEvents()

        // First 2 confirmed (FIFO)
        verify(eventResponseRepository).updateFinalStatus(event.id, user1, FinalStatus.confirmed, null)
        verify(eventResponseRepository).updateFinalStatus(event.id, user2, FinalStatus.confirmed, null)
        // Third is waitlisted at position 1
        verify(eventResponseRepository).updateFinalStatus(event.id, user3, FinalStatus.waitlisted, 1)
        // confirmed_count = limit = 2
        verify(eventRepository).markStage2Triggered(event.id, 2)
    }

    @Test
    fun `scenario B - going below limit, all going confirmed then maybe fill spots`() {
        val event = makeEvent(limit = 5)
        val goingUser1 = UUID.randomUUID()
        val goingUser2 = UUID.randomUUID()
        val maybeUser1 = UUID.randomUUID()
        val maybeUser2 = UUID.randomUUID()

        val goingResponses = listOf(
            makeResponse(event.id, goingUser1),
            makeResponse(event.id, goingUser2)
        )
        val maybeResponses = listOf(
            makeResponse(event.id, maybeUser1),
            makeResponse(event.id, maybeUser2)
        )

        whenever(eventRepository.findEventsReadyForStage2()).thenReturn(listOf(event))
        whenever(eventResponseRepository.findGoingByEvent(event.id)).thenReturn(goingResponses)
        whenever(eventResponseRepository.findMaybeByEvent(event.id)).thenReturn(maybeResponses)

        scheduler.triggerStage2ForEligibleEvents()

        // All going confirmed
        verify(eventResponseRepository).updateFinalStatus(event.id, goingUser1, FinalStatus.confirmed, null)
        verify(eventResponseRepository).updateFinalStatus(event.id, goingUser2, FinalStatus.confirmed, null)
        // Maybe users fill remaining 3 spots, only 2 available
        verify(eventResponseRepository).updateFinalStatus(event.id, maybeUser1, FinalStatus.confirmed, null)
        verify(eventResponseRepository).updateFinalStatus(event.id, maybeUser2, FinalStatus.confirmed, null)
        // total confirmed = 2 going + 2 maybe = 4 (deficit logged, organizer notification needed)
        verify(eventRepository).markStage2Triggered(event.id, 4)
    }

    @Test
    fun `scenario B - going exactly equals limit, no maybe needed`() {
        val event = makeEvent(limit = 2)
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        val goingResponses = listOf(makeResponse(event.id, user1), makeResponse(event.id, user2))

        whenever(eventRepository.findEventsReadyForStage2()).thenReturn(listOf(event))
        whenever(eventResponseRepository.findGoingByEvent(event.id)).thenReturn(goingResponses)
        whenever(eventResponseRepository.findMaybeByEvent(event.id)).thenReturn(emptyList())

        scheduler.triggerStage2ForEligibleEvents()

        verify(eventResponseRepository).updateFinalStatus(event.id, user1, FinalStatus.confirmed, null)
        verify(eventResponseRepository).updateFinalStatus(event.id, user2, FinalStatus.confirmed, null)
        verify(eventResponseRepository, never()).updateFinalStatus(any(), any(), eq(FinalStatus.waitlisted), any())
        verify(eventRepository).markStage2Triggered(event.id, 2)
    }

    @Test
    fun `scenario A - multiple events processed independently`() {
        val event1 = makeEvent(limit = 1)
        val event2 = makeEvent(limit = 2)
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        val user3 = UUID.randomUUID()

        whenever(eventRepository.findEventsReadyForStage2()).thenReturn(listOf(event1, event2))
        whenever(eventResponseRepository.findGoingByEvent(event1.id))
            .thenReturn(listOf(makeResponse(event1.id, user1), makeResponse(event1.id, user2)))
        whenever(eventResponseRepository.findGoingByEvent(event2.id))
            .thenReturn(listOf(makeResponse(event2.id, user3)))

        scheduler.triggerStage2ForEligibleEvents()

        // Event1: Scenario A — user1 confirmed, user2 waitlisted
        verify(eventResponseRepository).updateFinalStatus(event1.id, user1, FinalStatus.confirmed, null)
        verify(eventResponseRepository).updateFinalStatus(event1.id, user2, FinalStatus.waitlisted, 1)
        verify(eventRepository).markStage2Triggered(event1.id, 1)

        // Event2: Scenario B — user3 confirmed
        verify(eventResponseRepository).updateFinalStatus(event2.id, user3, FinalStatus.confirmed, null)
        verify(eventRepository).markStage2Triggered(event2.id, 1)
    }

    @Test
    fun `exception in one event does not stop processing of others`() {
        val event1 = makeEvent(limit = 1)
        val event2 = makeEvent(limit = 1)
        val user = UUID.randomUUID()

        whenever(eventRepository.findEventsReadyForStage2()).thenReturn(listOf(event1, event2))
        whenever(eventResponseRepository.findGoingByEvent(event1.id))
            .thenThrow(RuntimeException("DB error"))
        whenever(eventResponseRepository.findGoingByEvent(event2.id))
            .thenReturn(listOf(makeResponse(event2.id, user)))

        // Should not throw
        scheduler.triggerStage2ForEligibleEvents()

        // Event2 still processed
        verify(eventResponseRepository).updateFinalStatus(event2.id, user, FinalStatus.confirmed, null)
        verify(eventRepository).markStage2Triggered(event2.id, 1)
    }
}
