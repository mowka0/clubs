package com.clubs.club

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.jooq.DSLContext

class ClubRepositoryTest {

    private val dsl: DSLContext = mock(DSLContext::class.java)
    private val repo = ClubRepository(dsl)

    @Test
    fun `buildFilterConditions returns empty list when no filters`() {
        val conditions = repo.buildFilterConditions(ClubFilters())
        assertTrue(conditions.isEmpty())
    }

    @Test
    fun `buildFilterConditions adds city condition`() {
        val conditions = repo.buildFilterConditions(ClubFilters(city = "Moscow"))
        assertEquals(1, conditions.size)
        assertTrue(conditions[0].toString().lowercase().contains("city"))
    }

    @Test
    fun `buildFilterConditions adds category condition`() {
        val conditions = repo.buildFilterConditions(ClubFilters(category = "sport"))
        assertEquals(1, conditions.size)
        assertTrue(conditions[0].toString().lowercase().contains("category"))
    }

    @Test
    fun `buildFilterConditions adds access_type condition`() {
        val conditions = repo.buildFilterConditions(ClubFilters(accessType = "open"))
        assertEquals(1, conditions.size)
        assertTrue(conditions[0].toString().lowercase().contains("access_type"))
    }

    @Test
    fun `buildFilterConditions adds price range conditions`() {
        val conditions = repo.buildFilterConditions(ClubFilters(priceMin = 100, priceMax = 500))
        assertEquals(2, conditions.size)
        val sql = conditions.joinToString(" ") { it.toString() }.lowercase()
        assertTrue(sql.contains("subscription_price"))
    }

    @Test
    fun `buildFilterConditions adds member limit conditions`() {
        val conditions = repo.buildFilterConditions(ClubFilters(memberLimitMin = 10, memberLimitMax = 50))
        assertEquals(2, conditions.size)
        val sql = conditions.joinToString(" ") { it.toString() }.lowercase()
        assertTrue(sql.contains("member_limit"))
    }

    @Test
    fun `buildFilterConditions adds search condition for name and description`() {
        val conditions = repo.buildFilterConditions(ClubFilters(search = "football"))
        assertEquals(1, conditions.size)
        val sql = conditions[0].toString().lowercase()
        assertTrue(sql.contains("name") || sql.contains("description"))
    }

    @Test
    fun `buildFilterConditions combines all filters`() {
        val filters = ClubFilters(
            city = "Moscow",
            category = "sport",
            accessType = "open",
            priceMin = 50
        )
        val conditions = repo.buildFilterConditions(filters)
        assertEquals(4, conditions.size)
    }

    @Test
    fun `ClubFilters defaults to null for all fields`() {
        val filters = ClubFilters()
        assertNull(filters.city)
        assertNull(filters.category)
        assertNull(filters.accessType)
        assertNull(filters.priceMin)
        assertNull(filters.priceMax)
        assertNull(filters.memberLimitMin)
        assertNull(filters.memberLimitMax)
        assertNull(filters.search)
    }

    @Test
    fun `ClubDto holds all expected fields`() {
        val now = java.time.OffsetDateTime.now()
        val id = java.util.UUID.randomUUID()
        val ownerId = java.util.UUID.randomUUID()
        val dto = ClubDto(
            id = id,
            ownerId = ownerId,
            name = "Test Club",
            description = "Description",
            city = "Moscow",
            category = "sport",
            accessType = "open",
            memberLimit = 20,
            subscriptionPrice = 100,
            avatarUrl = null,
            coverUrl = null,
            rules = null,
            applicationQuestion = null,
            telegramGroupId = null,
            activityRating = 0.0,
            confirmedCount = 0,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        assertEquals(id, dto.id)
        assertEquals(ownerId, dto.ownerId)
        assertEquals("Test Club", dto.name)
        assertEquals("sport", dto.category)
        assertEquals("open", dto.accessType)
        assertEquals(20, dto.memberLimit)
        assertEquals(100, dto.subscriptionPrice)
        assertTrue(dto.isActive)
    }

    @Test
    fun `UpdateClubDto all fields nullable`() {
        val dto = UpdateClubDto()
        assertNull(dto.name)
        assertNull(dto.city)
        assertNull(dto.category)
        assertNull(dto.subscriptionPrice)
    }
}
