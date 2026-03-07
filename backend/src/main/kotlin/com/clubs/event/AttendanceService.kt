package com.clubs.event

import com.clubs.config.NotFoundException
import com.clubs.config.ValidationException
import com.clubs.generated.jooq.enums.MembershipRole
import com.clubs.membership.MembershipRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

data class AttendanceEntry(val userId: UUID, val attended: Boolean)

data class AttendanceRequest(val attendances: List<AttendanceEntry>)

@Service
class AttendanceService(
    private val eventRepository: EventRepository,
    private val eventResponseRepository: EventResponseRepository,
    private val membershipRepository: MembershipRepository
) {

    fun recordAttendance(organizerId: UUID, eventId: UUID, attendances: List<AttendanceEntry>) {
        val event = eventRepository.findById(eventId)
            ?: throw NotFoundException("Event $eventId not found")

        val membership = membershipRepository.findByUserAndClub(organizerId, event.clubId)
            ?: throw AccessDeniedException("User is not a member of this club")
        if (membership.role != MembershipRole.organizer.literal) {
            throw AccessDeniedException("Only the club organizer can record attendance")
        }

        val now = OffsetDateTime.now()
        val twelveHoursAfterEvent = event.eventDatetime.plusHours(12)
        if (now.isBefore(twelveHoursAfterEvent)) {
            throw ValidationException("Attendance can only be recorded 12 hours after the event")
        }

        if (event.attendanceFinalized) {
            throw ValidationException("Attendance has already been finalized and cannot be changed")
        }

        // Check 48h edit window: if attendance was first recorded, organizer can only edit within 48h
        val firstRecordedAt = getAttendanceFirstRecordedAt(eventId)
        if (firstRecordedAt != null && now.isAfter(firstRecordedAt.plusHours(48))) {
            throw ValidationException("The 48-hour window for editing attendance has passed")
        }

        attendances.forEach { entry ->
            eventResponseRepository.updateAttendance(eventId, entry.userId, entry.attended)
        }

        eventRepository.markAttendanceFirstRecorded(eventId)
    }

    fun disputeAttendance(userId: UUID, eventId: UUID, targetUserId: UUID) {
        if (userId != targetUserId) {
            throw AccessDeniedException("You can only dispute your own attendance record")
        }

        val event = eventRepository.findById(eventId)
            ?: throw NotFoundException("Event $eventId not found")

        if (event.attendanceFinalized) {
            throw ValidationException("Attendance has already been finalized and cannot be disputed")
        }

        val response = eventResponseRepository.findByEventAndUser(eventId, userId)
            ?: throw NotFoundException("No attendance record found for user in event $eventId")

        if (response.attended != false) {
            throw ValidationException("You can only dispute an 'absent' attendance mark")
        }
    }

    private fun getAttendanceFirstRecordedAt(eventId: UUID): OffsetDateTime? {
        // We use a raw query to check the attendance_first_recorded_at column added in V10
        return eventRepository.getAttendanceFirstRecordedAt(eventId)
    }
}
