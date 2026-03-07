package com.clubs.reputation

import com.clubs.config.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.core.Authentication
import java.time.OffsetDateTime
import java.util.UUID

class ReputationControllerTest {

    private lateinit var reputationService: ReputationService
    private lateinit var controller: ReputationController
    private lateinit var authentication: Authentication

    private val userId = UUID.randomUUID()
    private val clubId = UUID.randomUUID()
    private val now = OffsetDateTime.now()

    private fun makeReputation(uid: UUID = userId, cid: UUID = clubId) = ReputationDto(
        id = UUID.randomUUID(),
        userId = uid,
        clubId = cid,
        reliabilityIndex = 100,
        promiseFulfillmentPct = 75.0,
        spontaneityCount = 1,
        totalConfirmed = 4,
        totalAttended = 3,
        createdAt = now,
        updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        reputationService = mock()
        controller = ReputationController(reputationService)
        authentication = mock()
        whenever(authentication.principal).thenReturn(userId.toString())
    }

    @Test
    fun `getClubReputation returns list sorted by service`() {
        val list = listOf(makeReputation(), makeReputation(uid = UUID.randomUUID()))
        whenever(reputationService.getClubReputation(clubId)).thenReturn(list)

        val response = controller.getClubReputation(clubId)

        assertEquals(200, response.statusCode.value())
        assertEquals(2, response.body!!.size)
    }

    @Test
    fun `getClubReputation returns empty list when no reputations`() {
        whenever(reputationService.getClubReputation(clubId)).thenReturn(emptyList())

        val response = controller.getClubReputation(clubId)

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.isEmpty())
    }

    @Test
    fun `getUserClubReputation returns reputation when found`() {
        val rep = makeReputation()
        whenever(reputationService.getUserClubReputation(userId, clubId)).thenReturn(rep)

        val response = controller.getUserClubReputation(userId, clubId)

        assertEquals(200, response.statusCode.value())
        assertEquals(rep.reliabilityIndex, response.body!!.reliabilityIndex)
    }

    @Test
    fun `getUserClubReputation throws 404 when not found`() {
        whenever(reputationService.getUserClubReputation(userId, clubId)).thenReturn(null)

        assertThrows<NotFoundException> {
            controller.getUserClubReputation(userId, clubId)
        }
    }

    @Test
    fun `getMyReputations returns all reputations for current user`() {
        val list = listOf(makeReputation(), makeReputation(cid = UUID.randomUUID()))
        whenever(reputationService.getAllUserReputations(userId)).thenReturn(list)

        val response = controller.getMyReputations(authentication)

        assertEquals(200, response.statusCode.value())
        assertEquals(2, response.body!!.size)
        verify(reputationService).getAllUserReputations(userId)
    }

    @Test
    fun `getMyReputations returns empty list when no clubs`() {
        whenever(reputationService.getAllUserReputations(userId)).thenReturn(emptyList())

        val response = controller.getMyReputations(authentication)

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.isEmpty())
    }
}
