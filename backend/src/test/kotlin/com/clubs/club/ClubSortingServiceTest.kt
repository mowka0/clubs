package com.clubs.club

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class ClubSortingServiceTest {

    private val service = ClubSortingService()
    private val now = OffsetDateTime.now()

    private fun makeClub(
        createdAt: OffsetDateTime = now,
        activityRating: Double = 0.0,
        confirmedCount: Int = 0,
        memberLimit: Int = 20
    ): ClubDto = ClubDto(
        id = UUID.randomUUID(),
        ownerId = UUID.randomUUID(),
        name = "Club",
        description = null,
        city = null,
        category = "sport",
        accessType = "open",
        memberLimit = memberLimit,
        subscriptionPrice = 100,
        avatarUrl = null,
        coverUrl = null,
        rules = null,
        applicationQuestion = null,
        telegramGroupId = null,
        activityRating = activityRating,
        confirmedCount = confirmedCount,
        isActive = true,
        createdAt = createdAt,
        updatedAt = now
    )

    // --- Newness score ---

    @Test
    fun `newness score is 1_0 for brand new club`() {
        val score = service.computeNewnessScore(now, now)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `newness score is 1_0 for club created exactly 2 weeks ago`() {
        val twoWeeksAgo = now.minusWeeks(2).plusSeconds(1) // just inside the 2-week window
        val score = service.computeNewnessScore(twoWeeksAgo, now)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `newness score is 0_0 for club older than 2 months`() {
        val threeMonthsAgo = now.minusMonths(3)
        val score = service.computeNewnessScore(threeMonthsAgo, now)
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun `newness score is between 0 and 1 for club in decay window`() {
        // Club created exactly at the midpoint between 2-week and 2-month cutoffs
        val twoWeeks = now.minusWeeks(2)
        val twoMonths = now.minusMonths(2)
        val midpoint = OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochSecond((twoWeeks.toEpochSecond() + twoMonths.toEpochSecond()) / 2),
            now.offset
        )
        val score = service.computeNewnessScore(midpoint, now)
        assertTrue(score > 0.0 && score < 1.0, "Expected score between 0 and 1, got $score")
        assertEquals(0.5, score, 0.05)
    }

    // --- Relevance score ---

    @Test
    fun `relevance score increases with higher activity_rating`() {
        val low = makeClub(activityRating = 1.0)
        val high = makeClub(activityRating = 5.0)
        assertTrue(service.computeRelevanceScore(high) > service.computeRelevanceScore(low))
    }

    @Test
    fun `relevance score includes newness bonus for new club`() {
        val newClub = makeClub(createdAt = now, activityRating = 1.0)
        val oldClub = makeClub(createdAt = now.minusMonths(3), activityRating = 1.0)
        assertTrue(service.computeRelevanceScore(newClub) > service.computeRelevanceScore(oldClub))
    }

    // --- Promo tags ---

    @Test
    fun `new club gets Новый tag`() {
        val club = makeClub(createdAt = now.minusDays(3))
        val tags = service.computePromoTags(club)
        assertTrue("Новый" in tags)
    }

    @Test
    fun `old club does not get Новый tag`() {
        val club = makeClub(createdAt = now.minusMonths(2))
        val tags = service.computePromoTags(club)
        assertFalse("Новый" in tags)
    }

    @Test
    fun `club with less than 80 percent full gets Свободные места tag`() {
        val club = makeClub(confirmedCount = 5, memberLimit = 50) // 10% full
        val tags = service.computePromoTags(club)
        assertTrue("Свободные места" in tags)
    }

    @Test
    fun `club at 80 percent full does NOT get Свободные места tag`() {
        val club = makeClub(confirmedCount = 16, memberLimit = 20) // exactly 80%
        val tags = service.computePromoTags(club)
        assertFalse("Свободные места" in tags)
    }

    @Test
    fun `top 10 percent fill rate club gets Популярный tag`() {
        // 10 clubs: 9 with 0% full, 1 with 100% full → that 1 is top 10%
        val clubs = (1..9).map { makeClub(confirmedCount = 0, memberLimit = 20) } +
                listOf(makeClub(confirmedCount = 20, memberLimit = 20))
        val popular = clubs.last()
        val tags = service.computePromoTags(popular, clubs)
        assertTrue("Популярный" in tags)
    }

    @Test
    fun `club below top 10 percent does NOT get Популярный tag`() {
        // 10 clubs with same fill rate → none in top 10%
        val clubs = (1..10).map { makeClub(confirmedCount = 10, memberLimit = 20) }
        val tags = service.computePromoTags(clubs.first(), clubs)
        // All clubs have the same fill rate — the threshold equals their rate,
        // so all qualify. Let's test that a club below the top does NOT qualify.
        val lowClub = makeClub(confirmedCount = 0, memberLimit = 20)
        val allClubs = clubs + listOf(lowClub)
        val tagsLow = service.computePromoTags(lowClub, allClubs)
        assertFalse("Популярный" in tagsLow)
    }

    // --- Enrich list ---

    @Test
    fun `enrichWithPromoTags sets promoTags on each club`() {
        val clubs = listOf(
            makeClub(createdAt = now.minusDays(3)),  // new
            makeClub(createdAt = now.minusMonths(3)) // old
        )
        val enriched = service.enrichWithPromoTags(clubs)
        assertEquals(2, enriched.size)
        assertTrue("Новый" in enriched[0].promoTags)
        assertFalse("Новый" in enriched[1].promoTags)
    }
}
