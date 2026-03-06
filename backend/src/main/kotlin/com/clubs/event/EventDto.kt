package com.clubs.event

import java.time.OffsetDateTime
import java.util.UUID

data class EventDto(
    val id: UUID,
    val clubId: UUID,
    val title: String,
    val description: String?,
    val location: String?,
    val eventDatetime: OffsetDateTime,
    val participantLimit: Int,
    val confirmedCount: Int,
    val votingOpensDaysBefore: Int,
    val status: String,
    val stage2Triggered: Boolean,
    val attendanceFinalized: Boolean,
    val attendanceFinalizedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class CreateEventDto(
    val clubId: UUID,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val eventDatetime: OffsetDateTime,
    val participantLimit: Int,
    val votingOpensDaysBefore: Int = 3
)

data class UpdateEventDto(
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val eventDatetime: OffsetDateTime? = null,
    val participantLimit: Int? = null,
    val votingOpensDaysBefore: Int? = null
)
