package com.clubs.invite

import java.time.OffsetDateTime
import java.util.UUID

data class InviteLinkDto(
    val id: UUID,
    val clubId: UUID,
    val code: String,
    val isSingleUse: Boolean,
    val isUsed: Boolean,
    val createdBy: UUID,
    val createdAt: OffsetDateTime,
    val usedAt: OffsetDateTime?
)

data class GenerateInviteLinkRequest(
    val isSingleUse: Boolean = false
)

data class InviteLinkResponse(
    val code: String,
    val link: String,
    val isSingleUse: Boolean
)
