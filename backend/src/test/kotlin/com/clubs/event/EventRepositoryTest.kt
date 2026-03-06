package com.clubs.event

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.jooq.DSLContext
import java.time.OffsetDateTime
import java.util.UUID

class EventRepositoryTest {

    private val dsl: DSLContext = mock(DSLContext::class.java)
    private val repo = EventRepository(dsl)

    @Test
    fun `EventDto holds all expected fields`() {
        val now = OffsetDateTime.now()
        val id = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        val dto = EventDto(
            id = id,
            clubId = clubId,
            title = "Test Event",
            description = "Description",
            location = "Moscow",
            eventDatetime = now.plusDays(7),
            participantLimit = 20,
            confirmedCount = 0,
            votingOpensDaysBefore = 3,
            status = "upcoming",
            stage2Triggered = false,
            attendanceFinalized = false,
            attendanceFinalizedAt = null,
            createdAt = now,
            updatedAt = now
        )
        assertEquals(id, dto.id)
        assertEquals(clubId, dto.clubId)
        assertEquals("Test Event", dto.title)
        assertEquals(20, dto.participantLimit)
        assertEquals(0, dto.confirmedCount)
        assertEquals(3, dto.votingOpensDaysBefore)
        assertEquals("upcoming", dto.status)
        assertFalse(dto.stage2Triggered)
        assertFalse(dto.attendanceFinalized)
        assertNull(dto.attendanceFinalizedAt)
    }

    @Test
    fun `CreateEventDto defaults votingOpensDaysBefore to 3`() {
        val dto = CreateEventDto(
            clubId = UUID.randomUUID(),
            title = "Event",
            eventDatetime = OffsetDateTime.now().plusDays(5),
            participantLimit = 10
        )
        assertEquals(3, dto.votingOpensDaysBefore)
        assertNull(dto.description)
        assertNull(dto.location)
    }

    @Test
    fun `UpdateEventDto all fields nullable`() {
        val dto = UpdateEventDto()
        assertNull(dto.title)
        assertNull(dto.description)
        assertNull(dto.location)
        assertNull(dto.eventDatetime)
        assertNull(dto.participantLimit)
        assertNull(dto.votingOpensDaysBefore)
    }

    @Test
    fun `UpdateEventDto can hold partial update values`() {
        val newDatetime = OffsetDateTime.now().plusDays(10)
        val dto = UpdateEventDto(title = "New Title", eventDatetime = newDatetime)
        assertEquals("New Title", dto.title)
        assertEquals(newDatetime, dto.eventDatetime)
        assertNull(dto.participantLimit)
    }
}
