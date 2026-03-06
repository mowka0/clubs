package com.clubs.club

import com.clubs.config.ValidationException
import com.clubs.storage.FileStorageService
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.OffsetDateTime
import java.util.UUID

class ClubServiceTest {

    private lateinit var clubRepository: ClubRepository
    private lateinit var dsl: DSLContext
    private lateinit var service: ClubService
    private lateinit var serviceWithStorage: ClubService
    private lateinit var fileStorageService: FileStorageService

    private val ownerId = UUID.randomUUID()

    private fun makeClubDto(): ClubDto {
        val now = OffsetDateTime.now()
        return ClubDto(
            id = UUID.randomUUID(),
            ownerId = ownerId,
            name = "Test Club",
            description = "A description",
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
    }

    @BeforeEach
    fun setUp() {
        clubRepository = mock()
        // RETURNS_DEEP_STUBS: jOOQ insert chain is mocked without explicit setup
        dsl = mock(defaultAnswer = org.mockito.Mockito.RETURNS_DEEP_STUBS)
        fileStorageService = mock()
        service = ClubService(clubRepository, dsl, null)
        serviceWithStorage = ClubService(clubRepository, dsl, fileStorageService)
    }

    private fun stubCreate() {
        whenever(clubRepository.create(any())).thenReturn(makeClubDto())
    }

    private fun stubOwnerCount(count: Int) {
        whenever(clubRepository.countByOwner(ownerId)).thenReturn(count)
    }

    // --- Validation: name ---

    @Test
    fun `createClub throws when name exceeds 60 characters`() {
        assertThrows<ValidationException> {
            service.createClub(ownerId, "A".repeat(61), null, null, "sport", "open", 20, 100)
        }
    }

    @Test
    fun `createClub accepts name of exactly 60 characters`() {
        stubOwnerCount(0)
        stubCreate()
        assertNotNull(service.createClub(ownerId, "A".repeat(60), null, null, "sport", "open", 20, 100))
    }

    // --- Validation: description ---

    @Test
    fun `createClub throws when description exceeds 500 characters`() {
        assertThrows<ValidationException> {
            service.createClub(ownerId, "Valid", "B".repeat(501), null, "sport", "open", 20, 100)
        }
    }

    @Test
    fun `createClub accepts description of exactly 500 characters`() {
        stubOwnerCount(0)
        stubCreate()
        assertNotNull(service.createClub(ownerId, "Valid", "B".repeat(500), null, "sport", "open", 20, 100))
    }

    // --- Validation: memberLimit ---

    @Test
    fun `createClub throws when memberLimit is below 10`() {
        assertThrows<ValidationException> {
            service.createClub(ownerId, "Test", null, null, "sport", "open", 9, 100)
        }
    }

    @Test
    fun `createClub throws when memberLimit is above 80`() {
        assertThrows<ValidationException> {
            service.createClub(ownerId, "Test", null, null, "sport", "open", 81, 100)
        }
    }

    @Test
    fun `createClub accepts memberLimit boundary value 10`() {
        stubOwnerCount(0)
        stubCreate()
        assertNotNull(service.createClub(ownerId, "Test", null, null, "sport", "open", 10, 100))
    }

    @Test
    fun `createClub accepts memberLimit boundary value 80`() {
        stubOwnerCount(0)
        stubCreate()
        assertNotNull(service.createClub(ownerId, "Test", null, null, "sport", "open", 80, 100))
    }

    // --- Organizer club limit ---

    @Test
    fun `createClub throws when organizer already has 10 clubs`() {
        stubOwnerCount(10)
        assertThrows<ValidationException> {
            service.createClub(ownerId, "Club 11", null, null, "sport", "open", 20, 100)
        }
    }

    @Test
    fun `createClub succeeds when organizer has 9 clubs`() {
        stubOwnerCount(9)
        stubCreate()
        assertNotNull(service.createClub(ownerId, "Club 10", null, null, "sport", "open", 20, 100))
    }

    // --- Avatar upload ---

    @Test
    fun `createClub does not call uploadFile when fileStorageService is null`() {
        stubOwnerCount(0)
        stubCreate()
        service.createClub(
            ownerId, "Test", null, null, "sport", "open", 20, 100,
            avatarBytes = byteArrayOf(1, 2, 3), avatarFileName = "avatar.jpg"
        )
        verifyNoInteractions(fileStorageService)
    }

    @Test
    fun `createClub calls uploadFile when storage and bytes are provided`() {
        stubOwnerCount(0)
        whenever(fileStorageService.uploadFile(any(), any())).thenReturn("https://s3.example.com/avatar.jpg")
        stubCreate()

        serviceWithStorage.createClub(
            ownerId, "Test", null, null, "sport", "open", 20, 100,
            avatarBytes = byteArrayOf(1, 2, 3), avatarFileName = "avatar.jpg"
        )

        verify(fileStorageService).uploadFile(any(), any())
    }

    @Test
    fun `createClub skips uploadFile when avatarBytes is null`() {
        stubOwnerCount(0)
        stubCreate()
        serviceWithStorage.createClub(ownerId, "Test", null, null, "sport", "open", 20, 100)
        verify(fileStorageService, never()).uploadFile(any(), any())
    }

    @Test
    fun `createClub passes null avatarUrl to repository when no storage configured`() {
        stubOwnerCount(0)
        stubCreate()
        service.createClub(
            ownerId, "Test", null, null, "sport", "open", 20, 100,
            avatarBytes = byteArrayOf(1, 2, 3), avatarFileName = "avatar.jpg"
        )
        val captor = argumentCaptor<CreateClubDto>()
        verify(clubRepository).create(captor.capture())
        assertNull(captor.firstValue.avatarUrl)
    }

    @Test
    fun `createClub passes uploaded avatarUrl to repository`() {
        stubOwnerCount(0)
        val url = "https://s3.example.com/avatar.jpg"
        whenever(fileStorageService.uploadFile(any(), any())).thenReturn(url)
        stubCreate()

        serviceWithStorage.createClub(
            ownerId, "Test", null, null, "sport", "open", 20, 100,
            avatarBytes = byteArrayOf(1, 2, 3), avatarFileName = "avatar.jpg"
        )

        val captor = argumentCaptor<CreateClubDto>()
        verify(clubRepository).create(captor.capture())
        assertEquals(url, captor.firstValue.avatarUrl)
    }

    // --- Revenue calculator ---

    @Test
    fun `calculateRevenue returns correct split for 100 Stars and 50 members`() {
        val revenue = service.calculateRevenue(price = 100, memberLimit = 50)
        assertEquals(5000, revenue.totalRevenue)
        assertEquals(4000, revenue.organizerShare)
        assertEquals(1000, revenue.platformShare)
    }

    @Test
    fun `calculateRevenue returns correct 80-20 split for 200 Stars and 10 members`() {
        val revenue = service.calculateRevenue(price = 200, memberLimit = 10)
        assertEquals(2000, revenue.totalRevenue)
        assertEquals(1600, revenue.organizerShare)
        assertEquals(400, revenue.platformShare)
    }

    @Test
    fun `calculateRevenue shares always sum to totalRevenue`() {
        val revenue = service.calculateRevenue(price = 73, memberLimit = 33)
        assertEquals(revenue.totalRevenue, revenue.organizerShare + revenue.platformShare)
    }

    @Test
    fun `MonthlyRevenueDto fields are set correctly`() {
        val dto = MonthlyRevenueDto(totalRevenue = 1000, organizerShare = 800, platformShare = 200)
        assertEquals(1000, dto.totalRevenue)
        assertEquals(800, dto.organizerShare)
        assertEquals(200, dto.platformShare)
    }
}
