package com.clubs.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "storage.s3")
data class S3Properties(
    val endpoint: String = "http://localhost:9000",
    val bucket: String = "clubs",
    val accessKey: String = "minioadmin",
    val secretKey: String = "minioadmin",
    val region: String = "us-east-1",
    /** true = path-style URLs (required for MinIO); false = virtual-hosted (Yandex Cloud) */
    val pathStyleAccess: Boolean = true
)
