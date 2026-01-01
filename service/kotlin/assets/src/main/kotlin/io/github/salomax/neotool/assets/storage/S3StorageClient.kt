package io.github.salomax.neotool.assets.storage

import io.github.salomax.neotool.assets.exception.StorageUnavailableException
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.retry.RetryUtils
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.time.Duration

/**
 * S3-compatible storage client implementation.
 *
 * Works with:
 * - AWS S3
 * - Cloudflare R2
 * - MinIO (development)
 * - Any S3-compatible storage
 *
 * Implements [StorageClient] interface for provider abstraction.
 */
@Singleton
class S3StorageClient(
    private val s3Client: S3Client,
    private val storageProperties: StorageProperties,
) : StorageClient {
    private val logger = LoggerFactory.getLogger(S3StorageClient::class.java)

    override fun generatePresignedUploadUrl(
        bucket: String,
        storageKey: String,
        mimeType: String,
        ttlSeconds: Long,
    ): String {
        logger.debug("Generating presigned upload URL for bucket: $bucket, key: $storageKey, mimeType: $mimeType")

        return try {
            val presigner = createPresigner()

            val putRequest =
                PutObjectRequest
                    .builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .contentType(mimeType)
                    .build()

            val presignRequest =
                PutObjectPresignRequest
                    .builder()
                    .signatureDuration(Duration.ofSeconds(ttlSeconds))
                    .putObjectRequest(putRequest)
                    .build()

            val presignedRequest = presigner.presignPutObject(presignRequest)
            presigner.close()

            val url = presignedRequest.url().toString()
            logger.debug("Generated presigned upload URL: $url")
            url
        } catch (e: NoSuchBucketException) {
            logger.error("Bucket does not exist: $bucket", e)
            throw StorageUnavailableException(
                "Storage bucket '$bucket' does not exist. Please contact administrator.",
                e,
            )
        } catch (e: SdkException) {
            logger.error("Failed to generate presigned upload URL: ${e.message}", e)
            throw StorageUnavailableException(
                "Storage service is currently unavailable. Please try again later.",
                e,
            )
        } catch (e: Exception) {
            logger.error("Unexpected error generating presigned upload URL: ${e.message}", e)
            throw StorageUnavailableException(
                "Storage service is currently unavailable. Please try again later.",
                e,
            )
        }
    }

    override fun generatePresignedDownloadUrl(
        bucket: String,
        storageKey: String,
        ttlSeconds: Long,
    ): String {
        logger.debug("Generating presigned download URL for bucket: $bucket, key: $storageKey")

        return try {
            val presigner = createPresigner()

            val getRequest =
                GetObjectRequest
                    .builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .build()

            val presignRequest =
                GetObjectPresignRequest
                    .builder()
                    .signatureDuration(Duration.ofSeconds(ttlSeconds))
                    .getObjectRequest(getRequest)
                    .build()

            val presignedRequest = presigner.presignGetObject(presignRequest)
            presigner.close()

            val url = presignedRequest.url().toString()
            logger.debug("Generated presigned download URL: $url")
            url
        } catch (e: NoSuchBucketException) {
            logger.error("Bucket does not exist: $bucket", e)
            throw StorageUnavailableException(
                "Storage bucket '$bucket' does not exist. Please contact administrator.",
                e,
            )
        } catch (e: SdkException) {
            logger.error("Failed to generate presigned download URL: ${e.message}", e)
            throw StorageUnavailableException(
                "Storage service is currently unavailable. Please try again later.",
                e,
            )
        } catch (e: Exception) {
            logger.error("Unexpected error generating presigned download URL: ${e.message}", e)
            throw StorageUnavailableException(
                "Storage service is currently unavailable. Please try again later.",
                e,
            )
        }
    }

    override fun objectExists(
        bucket: String,
        storageKey: String,
    ): Boolean {
        logger.debug("Checking if object exists: bucket=$bucket, key=$storageKey")

        return try {
            val headRequest =
                HeadObjectRequest
                    .builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .build()

            s3Client.headObject(headRequest)
            logger.debug("Object exists: bucket=$bucket, key=$storageKey")
            true
        } catch (e: NoSuchBucketException) {
            logger.error("Bucket does not exist: $bucket", e)
            throw StorageUnavailableException(
                "Storage bucket '$bucket' does not exist. Please contact administrator.",
                e,
            )
        } catch (e: NoSuchKeyException) {
            logger.debug("Object does not exist: bucket=$bucket, key=$storageKey")
            false
        } catch (e: S3Exception) {
            // Check if it's a service unavailable error (503) or connection issue
            if (e.statusCode() == 503 || RetryUtils.isServiceException(e)) {
                logger.error("Storage service unavailable while checking object existence: ${e.message}", e)
                throw StorageUnavailableException(
                    "Storage service is currently unavailable. Please try again later.",
                    e,
                )
            }
            // For other S3 errors, log and rethrow as storage unavailable
            logger.error("S3 error while checking object existence: ${e.message}", e)
            throw StorageUnavailableException(
                "Storage service error: ${e.message}",
                e,
            )
        } catch (e: SdkException) {
            logger.error("SDK error while checking object existence: ${e.message}", e)
            throw StorageUnavailableException(
                "Storage service is currently unavailable. Please try again later.",
                e,
            )
        }
    }

    override fun getObjectMetadata(
        bucket: String,
        storageKey: String,
    ): StorageClient.ObjectMetadata? {
        logger.debug("Getting object metadata: bucket=$bucket, key=$storageKey")

        return try {
            val headRequest =
                HeadObjectRequest
                    .builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .build()

            val response = s3Client.headObject(headRequest)
            StorageClient.ObjectMetadata(
                sizeBytes = response.contentLength(),
                contentType = response.contentType(),
                etag = response.eTag(),
            )
        } catch (e: NoSuchBucketException) {
            logger.error("Bucket does not exist: $bucket", e)
            throw StorageUnavailableException(
                "Storage bucket '$bucket' does not exist. Please contact administrator.",
                e,
            )
        } catch (e: NoSuchKeyException) {
            logger.warn("Object not found for metadata: bucket=$bucket, key=$storageKey")
            null
        } catch (e: S3Exception) {
            // Check if it's a service unavailable error (503) or connection issue
            if (e.statusCode() == 503 || RetryUtils.isServiceException(e)) {
                logger.error("Storage service unavailable while getting metadata: ${e.message}", e)
                throw StorageUnavailableException(
                    "Storage service is currently unavailable. Please try again later.",
                    e,
                )
            }
            // For other S3 errors, log and rethrow as storage unavailable
            logger.error("S3 error while getting metadata: ${e.message}", e)
            throw StorageUnavailableException(
                "Storage service error: ${e.message}",
                e,
            )
        } catch (e: SdkException) {
            logger.error("SDK error while getting metadata: ${e.message}", e)
            throw StorageUnavailableException(
                "Storage service is currently unavailable. Please try again later.",
                e,
            )
        }
    }

    override fun deleteObject(
        bucket: String,
        storageKey: String,
    ): Boolean {
        logger.info("Deleting object: bucket=$bucket, key=$storageKey")

        return try {
            val deleteRequest =
                DeleteObjectRequest
                    .builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .build()

            s3Client.deleteObject(deleteRequest)
            logger.info("Successfully deleted object: bucket=$bucket, key=$storageKey")
            true
        } catch (e: NoSuchBucketException) {
            logger.error("Bucket does not exist: $bucket", e)
            throw StorageUnavailableException(
                "Storage bucket '$bucket' does not exist. Please contact administrator.",
                e,
            )
        } catch (e: NoSuchKeyException) {
            // Object doesn't exist - consider deletion successful
            logger.debug("Object not found for deletion (already deleted): bucket=$bucket, key=$storageKey")
            true
        } catch (e: S3Exception) {
            // Check if it's a service unavailable error (503) or connection issue
            if (e.statusCode() == 503 || RetryUtils.isServiceException(e)) {
                logger.error("Storage service unavailable while deleting object: ${e.message}", e)
                throw StorageUnavailableException(
                    "Storage service is currently unavailable. Please try again later.",
                    e,
                )
            }
            // For other S3 errors, log and rethrow as storage unavailable
            logger.error("S3 error while deleting object: ${e.message}", e)
            throw StorageUnavailableException(
                "Storage service error: ${e.message}",
                e,
            )
        } catch (e: SdkException) {
            logger.error("SDK error while deleting object: ${e.message}", e)
            throw StorageUnavailableException(
                "Storage service is currently unavailable. Please try again later.",
                e,
            )
        } catch (e: Exception) {
            logger.error("Unexpected error deleting object: bucket=$bucket, key=$storageKey", e)
            throw StorageUnavailableException(
                "Storage service is currently unavailable. Please try again later.",
                e,
            )
        }
    }

    override fun generatePublicUrl(
        bucket: String,
        storageKey: String,
    ): String {
        // Defensive check: public URLs should only be generated for public bucket
        if (bucket != storageProperties.publicBucket) {
            logger.warn(
                "Attempted to generate public URL for private bucket: $bucket. " +
                    "This should not happen - use presigned URLs for private assets.",
            )
        }

        val baseUrl = storageProperties.getPublicBaseUrl().trimEnd('/')
        return "$baseUrl/$storageKey"
    }

    /**
     * Create S3Presigner with same configuration as S3Client.
     */
    private fun createPresigner(): S3Presigner {
        val credentials =
            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                storageProperties.accessKey,
                storageProperties.secretKey,
            )

        val builder =
            S3Presigner
                .builder()
                .endpointOverride(URI.create(storageProperties.getEndpoint()))
                .region(Region.of(storageProperties.region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(credentials),
                )

        // Apply path-style access if configured (required for MinIO)
        if (storageProperties.forcePathStyle) {
            builder.serviceConfiguration(
                software.amazon.awssdk.services.s3.S3Configuration
                    .builder()
                    .pathStyleAccessEnabled(true)
                    .build(),
            )
        }

        return builder.build()
    }
}
