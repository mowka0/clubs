package com.clubs.club

import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class ClubSortingService {

    companion object {
        private const val NEWNESS_FULL_WEEKS = 2L      // 1.0 score for first 2 weeks
        private const val NEWNESS_ZERO_MONTHS = 2L     // 0.0 score after 2 months
        private const val FREE_SLOTS_THRESHOLD = 0.80  // < 80% full → "Свободные места"
        private const val POPULAR_PERCENTILE = 0.90    // top 10% by fill rate → "Популярный"
    }

    /**
     * Computes promo tags for a single club relative to the list of all visible clubs.
     * allClubs is used to determine the top 10% threshold for "Популярный".
     */
    fun computePromoTags(club: ClubDto, allClubs: List<ClubDto> = emptyList()): List<String> {
        val tags = mutableListOf<String>()
        val now = OffsetDateTime.now()

        // «Новый» — club created less than 2 weeks ago
        if (club.createdAt.isAfter(now.minusWeeks(NEWNESS_FULL_WEEKS))) {
            tags.add("Новый")
        }

        // «Популярный» — fill rate in top 10% of all clubs
        if (allClubs.isNotEmpty()) {
            val clubFillRate = fillRate(club)
            val threshold = popularityThreshold(allClubs)
            if (clubFillRate >= threshold) {
                tags.add("Популярный")
            }
        }

        // «Свободные места» — less than 80% full
        if (fillRate(club) < FREE_SLOTS_THRESHOLD) {
            tags.add("Свободные места")
        }

        return tags
    }

    /**
     * Computes the relevance score for in-memory sorting (mirrors the SQL formula).
     * Useful for testing and non-DB contexts.
     * Formula: activity_rating * 0.5 + newness_score * 0.3 (organizer_rating not yet stored)
     */
    fun computeRelevanceScore(club: ClubDto, now: OffsetDateTime = OffsetDateTime.now()): Double {
        val newnessScore = computeNewnessScore(club.createdAt, now)
        return club.activityRating * 0.5 + newnessScore * 0.3
    }

    /**
     * newness_score: 1.0 if created < 2 weeks ago
     *                0.0 if created > 2 months ago
     *                linear decay in between
     */
    fun computeNewnessScore(createdAt: OffsetDateTime, now: OffsetDateTime = OffsetDateTime.now()): Double {
        val fullScoreCutoff = now.minusWeeks(NEWNESS_FULL_WEEKS)
        val zeroScoreCutoff = now.minusMonths(NEWNESS_ZERO_MONTHS)
        return when {
            createdAt.isAfter(fullScoreCutoff) -> 1.0
            createdAt.isBefore(zeroScoreCutoff) -> 0.0
            else -> {
                val totalDecaySeconds = fullScoreCutoff.toEpochSecond() - zeroScoreCutoff.toEpochSecond()
                val clubAgeFromZero = createdAt.toEpochSecond() - zeroScoreCutoff.toEpochSecond()
                clubAgeFromZero.toDouble() / totalDecaySeconds
            }
        }
    }

    /**
     * Enriches a list of clubs with computed promoTags.
     * allClubs is passed to allow top-10% calculation.
     */
    fun enrichWithPromoTags(clubs: List<ClubDto>): List<ClubDto> {
        return clubs.map { club ->
            club.copy(promoTags = computePromoTags(club, clubs))
        }
    }

    private fun fillRate(club: ClubDto): Double =
        if (club.memberLimit > 0) club.confirmedCount.toDouble() / club.memberLimit else 0.0

    private fun popularityThreshold(allClubs: List<ClubDto>): Double {
        val sortedRates = allClubs.map { fillRate(it) }.sorted()
        val idx = (sortedRates.size * POPULAR_PERCENTILE).toInt().coerceAtMost(sortedRates.size - 1)
        return sortedRates[idx]
    }
}
