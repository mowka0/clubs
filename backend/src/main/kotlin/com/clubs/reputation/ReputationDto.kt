package com.clubs.reputation

import java.time.OffsetDateTime
import java.util.UUID

data class ReputationDto(
    val id: UUID,
    val userId: UUID,
    val clubId: UUID,
    val reliabilityIndex: Int,
    val promiseFulfillmentPct: Double,
    val spontaneityCount: Int,
    val totalConfirmed: Int,
    val totalAttended: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
