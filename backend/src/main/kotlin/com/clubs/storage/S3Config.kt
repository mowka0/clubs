package com.clubs.storage

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(S3Properties::class)
class S3Config(private val properties: S3Properties) {

    private val log = LoggerFactory.getLogger(S3Config::class.java)

    @Bean
    fun amazonS3(): AmazonS3 {
        val credentials = BasicAWSCredentials(properties.accessKey, properties.secretKey)
        val client = AmazonS3ClientBuilder.standard()
            .withEndpointConfiguration(
                AwsClientBuilder.EndpointConfiguration(properties.endpoint, properties.region)
            )
            .withCredentials(AWSStaticCredentialsProvider(credentials))
            .withPathStyleAccessEnabled(properties.pathStyleAccess)
            .build()

        ensureBucketExists(client)
        return client
    }

    private fun ensureBucketExists(client: AmazonS3) {
        try {
            if (!client.doesBucketExistV2(properties.bucket)) {
                client.createBucket(properties.bucket)
                log.info("S3 bucket '${properties.bucket}' created")
            } else {
                log.info("S3 bucket '${properties.bucket}' already exists")
            }
        } catch (ex: Exception) {
            log.warn("Could not verify/create S3 bucket '${properties.bucket}': ${ex.message}. File uploads will fail if bucket does not exist.")
        }
    }
}
