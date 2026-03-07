package com.clubs.event

import com.clubs.config.NotFoundException
import com.clubs.config.ValidationException
import com.clubs.membership.MembershipDto
import com.clubs.membership.MembershipRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.access.AccessDeniedException
import java.time.OffsetDateTime
import java.util.UUID

class AttendanceServiceTest {

    private lateinit var eventRepository: EventRepository
    private lateinit var eventResponseRepository: EventResponseRepository
    private lateinit var membershipRepository: MembershipRepository
    private lateinit var service: AttendanceService

    private val organizerId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val clubId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        eventRepository = mock()
        eventResponseRepository = mock()
        membershipRepository = mock()
        service = AttendanceService(eventRepository, eventResponseRepository, membershipRepository)
    }

    private fun makeEvent(
        eventDatetime: OffsetDateTime = OffsetDateTime.now().minusHours(13),
        attendanceFinalized: Boolean = false
    ) = EventDto(
        id = eventId,
        clubId = clubId,
        title = "Test",
        description = null,
        location = null,
        eventDatetime = eventDatetime,
        participantLimit = 10,
        confirmedCount = 5,
        votingOpensDaysBefore = 3,
        status = "completed",
        stage2Triggered = true,
        attendanceFinalized = attendanceFinalized,
        attendanceFinalizedAt = null,
        createdAt = OffsetDateTime.now().minusDays(1),
        updatedAt = OffsetDateTime.now()
    )

    private fun makeOrganizerMembership() = MembershipDto(
        id = UUID.randomUUID(),
        userId = organizerId,
        clubId = clubId,
        role = "organizer",
        status = "active",
        joinedAt = OffsetDateTime.now().minusDays(30),
        subscriptionExpiresAt = null,
        lockedSubscriptionPrice = null,
        createdAt = OffsetDateTime.now().minusDays(30),
        updatedAt = OffsetDateTime.now()
    )

    private fun makeMemberMembership() = MembershipDto(
        id = UUID.randomUUID(),
        userId = memberId,
        clubId = clubId,
        role = "member",
        status = "active",
        joinedAt = OffsetDateTime.now().minusDays(30),
        subscriptionExpiresAt = null,
        lockedSubscriptionPrice = null,
        createdAt = OffsetDateTime.now().minusDays(30),
        updatedAt = OffsetDateTime.now()
    )

    private fun makeResponse(attended: Boolean?) = EventResponseDto(
        id = UUID.randomUUID(),
        eventId = eventId,
        userId = memberId,
        stage1Status = "going",
        finalStatus = if (attended == null) "confirmed" else if (attended) "attended" else "absent",
        waitlistPosition = null,
        attended = attended,
        respondedAt = OffsetDateTime.now().minusHours(5),
        confirmedAt = OffsetDateTime.now().minusHours(5),
        createdAt = OffsetDateTime.now().minusHours(5),
        updatedAt = OffsetDateTime.now()
    )

    // ---- recordAttendance tests ----

    @Test
    fun `recordAttendance - event not found throws NotFoundException`() {
        whenever(eventRepository.findById(eventId)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.recordAttendance(organizerId, eventId, listOf(AttendanceEntry(memberId, true)))
        }
    }

    @Test
    fun `recordAttendance - non-organizer throws AccessDeniedException`() {
        val event = makeEvent()
        whenever(eventRepository.findById(eventId)).thenReturn(event)
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeMemberMembership())

        assertThrows<AccessDeniedException> {
            service.recordAttendance(organizerId, eventId, listOf(AttendanceEntry(memberId, true)))
        }
    }

    @Test
    fun `recordAttendance - too early (less than 12h after event) throws ValidationException`() {
        // event happened only 6 hours ago
        val event = makeEvent(eventDatetime = OffsetDateTime.now().minusHours(6))
        whenever(eventRepository.findById(eventId)).thenReturn(event)
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())

        assertThrows<ValidationException> {
            service.recordAttendance(organizerId, eventId, listOf(AttendanceEntry(memberId, true)))
        }
    }

    @Test
    fun `recordAttendance - already finalized throws ValidationException`() {
        val event = makeEvent(attendanceFinalized = true)
        whenever(eventRepository.findById(eventId)).thenReturn(event)
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())

        assertThrows<ValidationException> {
            service.recordAttendance(organizerId, eventId, listOf(AttendanceEntry(memberId, true)))
        }
    }

    @Test
    fun `recordAttendance - success on first recording`() {
        val event = makeEvent()
        whenever(eventRepository.findById(eventId)).thenReturn(event)
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())
        whenever(eventRepository.getAttendanceFirstRecordedAt(eventId)).thenReturn(null)

        service.recordAttendance(organizerId, eventId, listOf(AttendanceEntry(memberId, true)))

        verify(eventResponseRepository).updateAttendance(eventId, memberId, true)
        verify(eventRepository).markAttendanceFirstRecorded(eventId)
    }

    @Test
    fun `recordAttendance - within 48h window allows update`() {
        val event = makeEvent()
        val firstRecordedAt = OffsetDateTime.now().minusHours(24)
        whenever(eventRepository.findById(eventId)).thenReturn(event)
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())
        whenever(eventRepository.getAttendanceFirstRecordedAt(eventId)).thenReturn(firstRecordedAt)

        service.recordAttendance(organizerId, eventId, listOf(AttendanceEntry(memberId, false)))

        verify(eventResponseRepository).updateAttendance(eventId, memberId, false)
    }

    @Test
    fun `recordAttendance - after 48h window throws ValidationException`() {
        val event = makeEvent()
        val firstRecordedAt = OffsetDateTime.now().minusHours(49)
        whenever(eventRepository.findById(eventId)).thenReturn(event)
        whenever(membershipRepository.findByUserAndClub(organizerId, clubId)).thenReturn(makeOrganizerMembership())
        whenever(eventRepository.getAttendanceFirstRecordedAt(eventId)).thenReturn(firstRecordedAt)

        assertThrows<ValidationException> {
            service.recordAttendance(organizerId, eventId, listOf(AttendanceEntry(memberId, true)))
        }
    }

    // ---- disputeAttendance tests ----

    @Test
    fun `disputeAttendance - cannot dispute another user's record`() {
        val otherUserId = UUID.randomUUID()
        assertThrows<AccessDeniedException> {
            service.disputeAttendance(memberId, eventId, otherUserId)
        }
    }

    @Test
    fun `disputeAttendance - event not found throws NotFoundException`() {
        whenever(eventRepository.findById(eventId)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.disputeAttendance(memberId, eventId, memberId)
        }
    }

    @Test
    fun `disputeAttendance - finalized event throws ValidationException`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent(attendanceFinalized = true))

        assertThrows<ValidationException> {
            service.disputeAttendance(memberId, eventId, memberId)
        }
    }

    @Test
    fun `disputeAttendance - no response record throws NotFoundException`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEventAndUser(eventId, memberId)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.disputeAttendance(memberId, eventId, memberId)
        }
    }

    @Test
    fun `disputeAttendance - not marked as absent throws ValidationException`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEventAndUser(eventId, memberId))
            .thenReturn(makeResponse(attended = true))

        assertThrows<ValidationException> {
            service.disputeAttendance(memberId, eventId, memberId)
        }
    }

    @Test
    fun `disputeAttendance - marked as absent succeeds`() {
        whenever(eventRepository.findById(eventId)).thenReturn(makeEvent())
        whenever(eventResponseRepository.findByEventAndUser(eventId, memberId))
            .thenReturn(makeResponse(attended = false))

        // Should not throw
        assertDoesNotThrow {
            service.disputeAttendance(memberId, eventId, memberId)
        }
    }
}
