package io.github.salomax.neotool.assets.storage

import io.micronaut.context.annotation.Requires
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import mu.KotlinLogging
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * Validates that required storage buckets exist and are accessible at startup.
 *
 * Fails fast if buckets are missing or inaccessible, preventing runtime errors.
 * Skips validation when `test.use-mock-storage` is enabled (for tests).
 */
@Singleton
@Requires(property = "test.use-mock-storage", notEquals = "true")
class BucketValidator(
    private val s3Client: S3Client,
    private val storageProperties: StorageProperties,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Validate buckets at application startup.
     *
     * Checks that both public and private buckets exist and are accessible.
     * Throws exception if validation fails, preventing application startup.
     */
    @PostConstruct
    fun validateBuckets() {
        val bucketsToValidate =
            listOf(
                "public" to storageProperties.publicBucket,
                "private" to storageProperties.privateBucket,
            )

        bucketsToValidate.forEach { (type, bucketName) ->
            try {
                s3Client.headBucket { it.bucket(bucketName) }
                logger.info { "Bucket validated: $bucketName (type: $type)" }
            } catch (e: NoSuchBucketException) {
                logger.error { "Bucket does not exist: $bucketName (type: $type)" }
                throw IllegalStateException(
                    "Required bucket does not exist: $bucketName (type: $type). " +
                        "Please create the bucket before starting the service.",
                    e,
                )
            } catch (e: S3Exception) {
                if (e.statusCode() == 403) {
                    logger.error { "Access denied to bucket: $bucketName (type: $type)" }
                    throw IllegalStateException(
                        "Access denied to bucket: $bucketName (type: $type). " +
                            "Please check bucket permissions and credentials.",
                        e,
                    )
                }
                logger.error(e) { "Error validating bucket: $bucketName (type: $type)" }
                throw IllegalStateException(
                    "Failed to validate bucket: $bucketName (type: $type). " +
                        "Error: ${e.message}",
                    e,
                )
            } catch (e: Exception) {
                logger.error(e) { "Unexpected error validating bucket: $bucketName (type: $type)" }
                throw IllegalStateException(
                    "Unexpected error validating bucket: $bucketName (type: $type). " +
                        "Error: ${e.message}",
                    e,
                )
            }
        }

        logger.info {
            "All required buckets validated successfully: " +
                "public=${storageProperties.publicBucket}, " +
                "private=${storageProperties.privateBucket}"
        }
    }
}
