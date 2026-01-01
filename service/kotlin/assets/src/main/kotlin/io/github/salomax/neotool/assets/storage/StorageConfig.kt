package io.github.salomax.neotool.assets.storage

import io.github.salomax.neotool.assets.domain.AssetVisibility
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Factory
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI

/**
 * Configuration properties for S3-compatible storage (MinIO/Cloudflare R2).
 *
 * Mapped from application.yml `asset.storage` section.
 * Supports separate buckets for public and private assets.
 */
@ConfigurationProperties("asset.storage")
data class StorageProperties(
    var hostname: String,
    var port: Int,
    var useHttps: Boolean,
    var region: String,
    var publicBucket: String,
    var privateBucket: String,
    // Legacy bucket support for migration (optional)
    var bucket: String? = null,
    var accessKey: String,
    var secretKey: String,
    var publicBasePath: String,
    var forcePathStyle: Boolean,
    var uploadTtlSeconds: Long,
    // Default: 1 hour
    var downloadTtlSeconds: Long = 3600,
) {
    /**
     * Validate configuration at startup.
     */
    @PostConstruct
    fun validate() {
        require(publicBucket.isNotBlank()) { "publicBucket must not be blank" }
        require(privateBucket.isNotBlank()) { "privateBucket must not be blank" }
        require(publicBucket != privateBucket) {
            "publicBucket and privateBucket must be different. " +
                "Got: publicBucket='$publicBucket', privateBucket='$privateBucket'"
        }
        require(publicBasePath.isNotBlank()) { "publicBasePath must not be blank" }
    }

    /**
     * Build full endpoint URL from hostname, port and protocol.
     */
    fun getEndpoint(): String {
        val protocol = if (useHttps) "https" else "http"
        return "$protocol://$hostname:$port"
    }

    /**
     * Build full public base URL from hostname, port, protocol and base path.
     * Used for generating public CDN URLs for PUBLIC assets.
     */
    fun getPublicBaseUrl(): String {
        val protocol = if (useHttps) "https" else "http"
        val path = publicBasePath.trim('/')
        return "$protocol://$hostname:$port/$path"
    }

    /**
     * Get bucket name for a given visibility level.
     *
     * @param visibility Asset visibility (PUBLIC or PRIVATE)
     * @return Bucket name for the visibility level
     */
    fun getBucketForVisibility(visibility: AssetVisibility): String {
        return when (visibility) {
            AssetVisibility.PUBLIC -> publicBucket
            AssetVisibility.PRIVATE -> privateBucket
        }
    }
}

/**
 * S3 Client factory configuration.
 *
 * Creates S3Client bean configured for MinIO (dev) or Cloudflare R2 (prod).
 */
@Factory
class StorageConfig {
    /**
     * Create S3Client bean with configuration from StorageProperties.
     *
     * @param properties Storage configuration properties
     * @return Configured S3Client instance
     */
    @Singleton
    fun s3Client(properties: StorageProperties): S3Client {
        val credentials =
            AwsBasicCredentials.create(
                properties.accessKey,
                properties.secretKey,
            )

        return S3Client
            .builder()
            .endpointOverride(URI.create(properties.getEndpoint()))
            .region(Region.of(properties.region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .serviceConfiguration(
                S3Configuration
                    .builder()
                    .pathStyleAccessEnabled(properties.forcePathStyle)
                    .build(),
            ).build()
    }
}
