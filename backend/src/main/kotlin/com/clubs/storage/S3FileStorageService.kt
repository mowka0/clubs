package com.clubs.storage

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.clubs.config.ValidationException
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

@Service
class S3FileStorageService(
    private val amazonS3: AmazonS3,
    private val properties: S3Properties
) : FileStorageService {

    companion object {
        const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB

        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png")
        private val EXTENSION_TO_CONTENT_TYPE = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png"
        )
    }

    override fun uploadFile(bytes: ByteArray, key: String): String {
        if (bytes.size > MAX_FILE_SIZE_BYTES) {
            throw ValidationException("File size ${bytes.size} bytes exceeds the 5 MB limit")
        }

        val extension = key.substringAfterLast('.', "").lowercase()
        if (extension !in ALLOWED_EXTENSIONS) {
            throw ValidationException("File type '$extension' is not allowed. Only jpg and png are supported.")
        }

        val contentType = EXTENSION_TO_CONTENT_TYPE[extension] ?: "application/octet-stream"
        val metadata = ObjectMetadata().apply {
            contentLength = bytes.size.toLong()
            this.contentType = contentType
        }

        amazonS3.putObject(properties.bucket, key, ByteArrayInputStream(bytes), metadata)
        return getFileUrl(key)
    }

    override fun getFileUrl(key: String): String =
        "${properties.endpoint.trimEnd('/')}/${properties.bucket}/${key.trimStart('/')}"

    override fun deleteFile(key: String) {
        amazonS3.deleteObject(properties.bucket, key)
    }
}
