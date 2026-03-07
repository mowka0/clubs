package com.clubs.application

import java.time.OffsetDateTime
import java.util.UUID

data class ApplicationDto(
    val id: UUID,
    val userId: UUID,
    val clubId: UUID,
    val answerText: String?,
    val status: String,
    val rejectionReason: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class SubmitApplicationRequest(
    val answerText: String?
)

data class RejectApplicationRequest(
    val reason: String?
)
