package com.clubs.club

import java.time.OffsetDateTime
import java.util.UUID

data class ClubDto(
    val id: UUID,
    val ownerId: UUID,
    val name: String,
    val description: String?,
    val city: String?,
    val category: String,
    val accessType: String,
    val memberLimit: Int,
    val subscriptionPrice: Int,
    val avatarUrl: String?,
    val coverUrl: String?,
    val rules: String?,
    val applicationQuestion: String?,
    val telegramGroupId: Long?,
    val activityRating: Double,
    val confirmedCount: Int,
    val isActive: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class CreateClubDto(
    val ownerId: UUID,
    val name: String,
    val description: String?,
    val city: String?,
    val category: String,
    val accessType: String,
    val memberLimit: Int,
    val subscriptionPrice: Int,
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val rules: String? = null,
    val applicationQuestion: String? = null
)

data class UpdateClubDto(
    val name: String? = null,
    val description: String? = null,
    val city: String? = null,
    val category: String? = null,
    val accessType: String? = null,
    val memberLimit: Int? = null,
    val subscriptionPrice: Int? = null,
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val rules: String? = null,
    val applicationQuestion: String? = null,
    val telegramGroupId: Long? = null
)

data class MonthlyRevenueDto(
    val totalRevenue: Int,
    val organizerShare: Int,
    val platformShare: Int
)

data class ClubFilters(
    val city: String? = null,
    val category: String? = null,
    val accessType: String? = null,
    val priceMin: Int? = null,
    val priceMax: Int? = null,
    val memberLimitMin: Int? = null,
    val memberLimitMax: Int? = null,
    val search: String? = null
)

data class ClubMemberDto(
    val userId: UUID,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val avatarUrl: String?,
    val role: String,
    val joinedAt: OffsetDateTime,
    val reliabilityIndex: Int = 0
)

data class PagedClubsResponse(
    val content: List<ClubDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long
)
