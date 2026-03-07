package com.clubs.event

import java.time.OffsetDateTime
import java.util.UUID

data class EventResponseDto(
    val id: UUID,
    val eventId: UUID,
    val userId: UUID,
    val stage1Status: String?,
    val finalStatus: String?,
    val waitlistPosition: Int?,
    val attended: Boolean?,
    val respondedAt: OffsetDateTime,
    val confirmedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class VoteCountsDto(
    val going: Int,
    val maybe: Int,
    val notGoing: Int
)

data class EventStatsDto(
    val going: Int,
    val maybe: Int,
    val notGoing: Int,
    val confirmed: Int,
    val limit: Int
)
