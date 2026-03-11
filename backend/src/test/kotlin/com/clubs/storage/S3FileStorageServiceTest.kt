package com.clubs.storage

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.clubs.config.ValidationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

class S3FileStorageServiceTest {

    private val amazonS3: AmazonS3 = mock()
    private val properties = S3Properties(
        endpoint = "http://localhost:9000",
        bucket = "clubs",
        accessKey = "minioadmin",
        secretKey = "minioadmin",
        region = "us-east-1",
        pathStyleAccess = true
    )
    private lateinit var service: S3FileStorageService

    @BeforeEach
    fun setUp() {
        service = S3FileStorageService(amazonS3, properties)
    }

    // --- uploadFile ---

    @Test
    fun `uploadFile - uploads jpg and returns url`() {
        val bytes = ByteArray(100) { 0 }
        val key = "avatars/test.jpg"

        val url = service.uploadFile(bytes, key)

        verify(amazonS3).putObject(eq("clubs"), eq(key), any(), any<ObjectMetadata>())
        assertEquals("http://localhost:9000/clubs/avatars/test.jpg", url)
    }

    @Test
    fun `uploadFile - uploads png and returns url`() {
        val bytes = ByteArray(200) { 1 }
        val key = "avatars/cover.png"

        val url = service.uploadFile(bytes, key)

        verify(amazonS3).putObject(eq("clubs"), eq(key), any(), any<ObjectMetadata>())
        assertEquals("http://localhost:9000/clubs/avatars/cover.png", url)
    }

    @Test
    fun `uploadFile - rejects file over 5MB`() {
        val bigBytes = ByteArray(5 * 1024 * 1024 + 1) { 0 }

        assertThrows<ValidationException> {
            service.uploadFile(bigBytes, "avatars/big.jpg")
        }
        verifyNoInteractions(amazonS3)
    }

    @Test
    fun `uploadFile - rejects disallowed extension`() {
        val bytes = ByteArray(100) { 0 }

        assertThrows<ValidationException> {
            service.uploadFile(bytes, "avatars/file.gif")
        }
        verifyNoInteractions(amazonS3)
    }

    @Test
    fun `uploadFile - rejects file with no extension`() {
        val bytes = ByteArray(100) { 0 }

        assertThrows<ValidationException> {
            service.uploadFile(bytes, "avatars/noextension")
        }
        verifyNoInteractions(amazonS3)
    }

    @Test
    fun `uploadFile - accepts jpeg extension`() {
        val bytes = ByteArray(50) { 0 }

        val url = service.uploadFile(bytes, "photos/image.jpeg")

        verify(amazonS3).putObject(eq("clubs"), eq("photos/image.jpeg"), any(), any<ObjectMetadata>())
        assertTrue(url.contains("image.jpeg"))
    }

    @Test
    fun `uploadFile - exactly 5MB is accepted`() {
        val maxBytes = ByteArray(5 * 1024 * 1024) { 0 }

        assertDoesNotThrow {
            service.uploadFile(maxBytes, "avatars/exact.jpg")
        }
        verify(amazonS3).putObject(any(), any(), any(), any<ObjectMetadata>())
    }

    // --- getFileUrl ---

    @Test
    fun `getFileUrl - returns correct url without double slashes`() {
        val url = service.getFileUrl("avatars/user123.jpg")
        assertEquals("http://localhost:9000/clubs/avatars/user123.jpg", url)
    }

    @Test
    fun `getFileUrl - strips leading slash from key`() {
        val url = service.getFileUrl("/avatars/user123.jpg")
        assertEquals("http://localhost:9000/clubs/avatars/user123.jpg", url)
    }

    @Test
    fun `getFileUrl - strips trailing slash from endpoint`() {
        val propertiesWithTrailingSlash = properties.copy(endpoint = "http://localhost:9000/")
        val svc = S3FileStorageService(amazonS3, propertiesWithTrailingSlash)

        val url = svc.getFileUrl("avatars/file.png")
        assertEquals("http://localhost:9000/clubs/avatars/file.png", url)
    }

    // --- deleteFile ---

    @Test
    fun `deleteFile - delegates to S3`() {
        service.deleteFile("avatars/old.jpg")
        verify(amazonS3).deleteObject("clubs", "avatars/old.jpg")
    }
}
