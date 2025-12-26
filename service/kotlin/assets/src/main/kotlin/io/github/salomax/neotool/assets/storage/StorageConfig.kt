package io.github.salomax.neotool.assets.storage

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Factory
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
 * All properties come from configuration file - no defaults here.
 */
@ConfigurationProperties("asset.storage")
data class StorageProperties(
    var hostname: String,
    var port: Int,
    var useHttps: Boolean,
    var region: String,
    var bucket: String,
    var accessKey: String,
    var secretKey: String,
    var publicBasePath: String,
    var forcePathStyle: Boolean,
    var uploadTtlSeconds: Long,
) {
    /**
     * Build full endpoint URL from hostname, port and protocol.
     */
    fun getEndpoint(): String {
        val protocol = if (useHttps) "https" else "http"
        return "$protocol://$hostname:$port"
    }

    /**
     * Build full public base URL from hostname, port, protocol and base path.
     */
    fun getPublicBaseUrl(): String {
        val protocol = if (useHttps) "https" else "http"
        val path = publicBasePath.trim('/')
        return "$protocol://$hostname:$port/$path"
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
