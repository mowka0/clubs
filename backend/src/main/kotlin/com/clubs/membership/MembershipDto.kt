package com.clubs.membership

import java.time.OffsetDateTime
import java.util.UUID

data class MembershipDto(
    val id: UUID,
    val userId: UUID,
    val clubId: UUID,
    val role: String,
    val status: String,
    val joinedAt: OffsetDateTime,
    val subscriptionExpiresAt: OffsetDateTime?,
    val lockedSubscriptionPrice: Int?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
