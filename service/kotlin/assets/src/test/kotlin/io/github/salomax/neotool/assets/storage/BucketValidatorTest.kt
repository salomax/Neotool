package io.github.salomax.neotool.assets.storage

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.S3Exception
import java.util.function.Consumer

@DisplayName("BucketValidator Unit Tests")
class BucketValidatorTest {
    private lateinit var s3Client: S3Client
    private lateinit var storageProperties: StorageProperties
    private lateinit var bucketValidator: BucketValidator

    @BeforeEach
    fun setUp() {
        s3Client = mock()
        storageProperties =
            StorageProperties(
                hostname = "localhost",
                port = 9000,
                useHttps = false,
                region = "us-east-1",
                publicBucket = "test-public-bucket",
                privateBucket = "test-private-bucket",
                accessKey = "test-access-key",
                secretKey = "test-secret-key",
                publicBasePath = "/cdn",
                forcePathStyle = true,
                uploadTtlSeconds = 3600,
                downloadTtlSeconds = 3600,
            )
        bucketValidator = BucketValidator(s3Client, storageProperties)
    }

    @Test
    @DisplayName("should validate buckets successfully when both exist")
    fun `should validate buckets successfully when both exist`() {
        // Arrange
        val headResponse = HeadBucketResponse.builder().build()
        whenever(s3Client.headBucket(any<Consumer<HeadBucketRequest.Builder>>())).thenReturn(headResponse)

        // Act
        bucketValidator.validateBuckets()

        // Assert
        verify(s3Client, times(2)).headBucket(any<Consumer<HeadBucketRequest.Builder>>())
    }

    @Test
    @DisplayName("should throw IllegalStateException when public bucket does not exist")
    fun `should throw IllegalStateException when public bucket does not exist`() {
        // Arrange
        whenever(s3Client.headBucket(any<Consumer<HeadBucketRequest.Builder>>())).thenThrow(
            NoSuchBucketException.builder().message("Bucket not found").build(),
        )

        // Act & Assert
        assertThatThrownBy { bucketValidator.validateBuckets() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Required bucket does not exist")
            .hasMessageContaining(storageProperties.publicBucket)

        verify(s3Client).headBucket(any<Consumer<HeadBucketRequest.Builder>>())
    }

    @Test
    @DisplayName("should throw IllegalStateException when private bucket does not exist")
    fun `should throw IllegalStateException when private bucket does not exist`() {
        // Arrange
        val headResponse = HeadBucketResponse.builder().build()
        whenever(s3Client.headBucket(any<Consumer<HeadBucketRequest.Builder>>()))
            .thenReturn(headResponse) // Public bucket exists
            .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build()) // Private bucket missing

        // Act & Assert
        assertThatThrownBy { bucketValidator.validateBuckets() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Required bucket does not exist")
            .hasMessageContaining(storageProperties.privateBucket)

        verify(s3Client, times(2)).headBucket(any<Consumer<HeadBucketRequest.Builder>>())
    }

    @Test
    @DisplayName("should throw IllegalStateException when access denied to bucket")
    fun `should throw IllegalStateException when access denied to bucket`() {
        // Arrange
        val s3Exception =
            mock<S3Exception> {
                on { statusCode() }.thenReturn(403)
                on { message }.thenReturn("Access denied")
            }
        whenever(s3Client.headBucket(any<Consumer<HeadBucketRequest.Builder>>())).thenThrow(s3Exception)

        // Act & Assert
        assertThatThrownBy { bucketValidator.validateBuckets() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Access denied to bucket")
            .hasMessageContaining(storageProperties.publicBucket)

        verify(s3Client).headBucket(any<Consumer<HeadBucketRequest.Builder>>())
    }

    @Test
    @DisplayName("should throw IllegalStateException on other S3Exception")
    fun `should throw IllegalStateException on other S3Exception`() {
        // Arrange
        val s3Exception =
            mock<S3Exception> {
                on { statusCode() }.thenReturn(500)
                on { message }.thenReturn("Internal server error")
            }
        whenever(s3Client.headBucket(any<Consumer<HeadBucketRequest.Builder>>())).thenThrow(s3Exception)

        // Act & Assert
        assertThatThrownBy { bucketValidator.validateBuckets() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Failed to validate bucket")
            .hasMessageContaining(storageProperties.publicBucket)

        verify(s3Client).headBucket(any<Consumer<HeadBucketRequest.Builder>>())
    }

    @Test
    @DisplayName("should throw IllegalStateException on generic Exception")
    fun `should throw IllegalStateException on generic Exception`() {
        // Arrange
        whenever(s3Client.headBucket(any<Consumer<HeadBucketRequest.Builder>>()))
            .thenThrow(
                RuntimeException("Unexpected error"),
            )

        // Act & Assert
        assertThatThrownBy { bucketValidator.validateBuckets() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unexpected error validating bucket")
            .hasMessageContaining(storageProperties.publicBucket)

        verify(s3Client).headBucket(any<Consumer<HeadBucketRequest.Builder>>())
    }
}
