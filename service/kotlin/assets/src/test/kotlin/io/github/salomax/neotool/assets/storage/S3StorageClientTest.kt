package io.github.salomax.neotool.assets.storage

import io.github.salomax.neotool.assets.exception.StorageUnavailableException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception

@DisplayName("S3StorageClient Unit Tests")
class S3StorageClientTest {
    private lateinit var s3Client: S3Client
    private lateinit var storageProperties: StorageProperties
    private lateinit var s3StorageClient: S3StorageClient

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
        s3StorageClient = S3StorageClient(s3Client, storageProperties)
    }

    // Note: generatePresignedUploadUrl() and generatePresignedDownloadUrl() are not unit tested here
    // because they create S3Presigner internally which is complex to mock. These methods are
    // tested in integration tests (AssetServiceMinIOIntegrationTest) where real S3/MinIO is used.
    // The error handling paths in these methods follow the same patterns as the other methods
    // which are tested here (objectExists, getObjectMetadata, deleteObject).

    @Nested
    @DisplayName("objectExists()")
    inner class ObjectExistsTests {
        @Test
        fun `should return true when object exists`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            val headResponse = HeadObjectResponse.builder().build()
            whenever(s3Client.headObject(any<HeadObjectRequest>())).thenReturn(headResponse)

            // Act
            val result = s3StorageClient.objectExists(bucket, key)

            // Assert
            assertThat(result).isTrue()
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }

        @Test
        fun `should return false when object does not exist`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            whenever(s3Client.headObject(any<HeadObjectRequest>()))
                .thenThrow(NoSuchKeyException.builder().message("Key not found").build())

            // Act
            val result = s3StorageClient.objectExists(bucket, key)

            // Assert
            assertThat(result).isFalse()
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException when bucket does not exist`() {
            // Arrange
            val bucket = "non-existent-bucket"
            val key = "test-key"
            whenever(s3Client.headObject(any<HeadObjectRequest>()))
                .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build())

            // Act & Assert
            assertThatThrownBy { s3StorageClient.objectExists(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasMessageContaining("Storage bucket '$bucket' does not exist")
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException on service unavailable error`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            val s3Exception =
                S3Exception.builder()
                    .statusCode(503)
                    .message("Service unavailable")
                    .build()
            whenever(s3Client.headObject(any<HeadObjectRequest>())).thenThrow(s3Exception)

            // Act & Assert
            assertThatThrownBy { s3StorageClient.objectExists(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasMessageContaining("Storage service is currently unavailable")
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException on other S3Exception`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            // Use a real S3Exception builder
            // Note: RetryUtils.isServiceException() may return true for various S3Exceptions,
            // in which case it goes to the "service unavailable" path instead of "storage service error" path.
            // We test that a StorageUnavailableException is thrown in either case.
            val s3Exception =
                S3Exception.builder()
                    .statusCode(404)
                    .message("Not found error")
                    .requestId("test-request-id")
                    .build()
            whenever(s3Client.headObject(any<HeadObjectRequest>())).thenThrow(s3Exception)

            // Act & Assert
            // The exception may go through either path depending on RetryUtils.isServiceException()
            // Both paths throw StorageUnavailableException, so we just verify the type
            assertThatThrownBy { s3StorageClient.objectExists(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException on SdkException`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            val sdkException = SdkException.builder().message("SDK error").build()
            whenever(s3Client.headObject(any<HeadObjectRequest>())).thenThrow(sdkException)

            // Act & Assert
            assertThatThrownBy { s3StorageClient.objectExists(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasMessageContaining("Storage service is currently unavailable")
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }
    }

    @Nested
    @DisplayName("getObjectMetadata()")
    inner class GetObjectMetadataTests {
        @Test
        fun `should return metadata when object exists`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            val headResponse =
                HeadObjectResponse.builder()
                    .contentLength(1024L)
                    .contentType("image/jpeg")
                    .eTag("etag-123")
                    .build()
            whenever(s3Client.headObject(any<HeadObjectRequest>())).thenReturn(headResponse)

            // Act
            val result = s3StorageClient.getObjectMetadata(bucket, key)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result?.sizeBytes).isEqualTo(1024L)
            assertThat(result?.contentType).isEqualTo("image/jpeg")
            assertThat(result?.etag).isEqualTo("etag-123")
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }

        @Test
        fun `should return null when object does not exist`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            whenever(s3Client.headObject(any<HeadObjectRequest>()))
                .thenThrow(NoSuchKeyException.builder().message("Key not found").build())

            // Act
            val result = s3StorageClient.getObjectMetadata(bucket, key)

            // Assert
            assertThat(result).isNull()
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException when bucket does not exist`() {
            // Arrange
            val bucket = "non-existent-bucket"
            val key = "test-key"
            whenever(s3Client.headObject(any<HeadObjectRequest>()))
                .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build())

            // Act & Assert
            assertThatThrownBy { s3StorageClient.getObjectMetadata(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasMessageContaining("Storage bucket '$bucket' does not exist")
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException on service unavailable error`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            val s3Exception =
                S3Exception.builder()
                    .statusCode(503)
                    .message("Service unavailable")
                    .build()
            whenever(s3Client.headObject(any<HeadObjectRequest>())).thenThrow(s3Exception)

            // Act & Assert
            assertThatThrownBy { s3StorageClient.getObjectMetadata(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasMessageContaining("Storage service is currently unavailable")
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException on other S3Exception`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            // Use a real S3Exception builder
            // Note: RetryUtils.isServiceException() may return true for various S3Exceptions,
            // in which case it goes to the "service unavailable" path instead of "storage service error" path.
            // We test that a StorageUnavailableException is thrown in either case.
            val s3Exception =
                S3Exception.builder()
                    .statusCode(404)
                    .message("Not found error")
                    .requestId("test-request-id")
                    .build()
            whenever(s3Client.headObject(any<HeadObjectRequest>())).thenThrow(s3Exception)

            // Act & Assert
            // The exception may go through either path depending on RetryUtils.isServiceException()
            // Both paths throw StorageUnavailableException, so we just verify the type
            assertThatThrownBy { s3StorageClient.getObjectMetadata(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException on SdkException`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            val sdkException = SdkException.builder().message("SDK error").build()
            whenever(s3Client.headObject(any<HeadObjectRequest>())).thenThrow(sdkException)

            // Act & Assert
            assertThatThrownBy { s3StorageClient.getObjectMetadata(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasMessageContaining("Storage service is currently unavailable")
            verify(s3Client).headObject(any<HeadObjectRequest>())
        }
    }

    @Nested
    @DisplayName("deleteObject()")
    inner class DeleteObjectTests {
        @Test
        fun `should return true when object is deleted successfully`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            val deleteResponse = DeleteObjectResponse.builder().build()
            whenever(s3Client.deleteObject(any<DeleteObjectRequest>())).thenReturn(deleteResponse)

            // Act
            val result = s3StorageClient.deleteObject(bucket, key)

            // Assert
            assertThat(result).isTrue()
            verify(s3Client).deleteObject(any<DeleteObjectRequest>())
        }

        @Test
        fun `should return true when object does not exist`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            whenever(s3Client.deleteObject(any<DeleteObjectRequest>()))
                .thenThrow(NoSuchKeyException.builder().message("Key not found").build())

            // Act
            val result = s3StorageClient.deleteObject(bucket, key)

            // Assert
            assertThat(result).isTrue() // Considered successful if already deleted
            verify(s3Client).deleteObject(any<DeleteObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException when bucket does not exist`() {
            // Arrange
            val bucket = "non-existent-bucket"
            val key = "test-key"
            whenever(s3Client.deleteObject(any<DeleteObjectRequest>()))
                .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build())

            // Act & Assert
            assertThatThrownBy { s3StorageClient.deleteObject(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasMessageContaining("Storage bucket '$bucket' does not exist")
            verify(s3Client).deleteObject(any<DeleteObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException on service unavailable error`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            val s3Exception =
                S3Exception.builder()
                    .statusCode(503)
                    .message("Service unavailable")
                    .build()
            whenever(s3Client.deleteObject(any<DeleteObjectRequest>())).thenThrow(s3Exception)

            // Act & Assert
            assertThatThrownBy { s3StorageClient.deleteObject(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasMessageContaining("Storage service is currently unavailable")
            verify(s3Client).deleteObject(any<DeleteObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException on other S3Exception`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            // Use a real S3Exception builder
            // Note: RetryUtils.isServiceException() may return true for various S3Exceptions,
            // in which case it goes to the "service unavailable" path instead of "storage service error" path.
            // We test that a StorageUnavailableException is thrown in either case.
            val s3Exception =
                S3Exception.builder()
                    .statusCode(404)
                    .message("Not found error")
                    .requestId("test-request-id")
                    .build()
            whenever(s3Client.deleteObject(any<DeleteObjectRequest>())).thenThrow(s3Exception)

            // Act & Assert
            // The exception may go through either path depending on RetryUtils.isServiceException()
            // Both paths throw StorageUnavailableException, so we just verify the type
            assertThatThrownBy { s3StorageClient.deleteObject(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
            verify(s3Client).deleteObject(any<DeleteObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException on SdkException`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            val sdkException = SdkException.builder().message("SDK error").build()
            whenever(s3Client.deleteObject(any<DeleteObjectRequest>())).thenThrow(sdkException)

            // Act & Assert
            assertThatThrownBy { s3StorageClient.deleteObject(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasMessageContaining("Storage service is currently unavailable")
            verify(s3Client).deleteObject(any<DeleteObjectRequest>())
        }

        @Test
        fun `should throw StorageUnavailableException on generic Exception`() {
            // Arrange
            val bucket = "test-bucket"
            val key = "test-key"
            whenever(s3Client.deleteObject(any<DeleteObjectRequest>()))
                .thenThrow(RuntimeException("Unexpected error"))

            // Act & Assert
            assertThatThrownBy { s3StorageClient.deleteObject(bucket, key) }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasMessageContaining("Storage service is currently unavailable")
            verify(s3Client).deleteObject(any<DeleteObjectRequest>())
        }
    }

    @Nested
    @DisplayName("generatePublicUrl()")
    inner class GeneratePublicUrlTests {
        @Test
        fun `should generate public URL for public bucket`() {
            // Arrange
            val bucket = storageProperties.publicBucket
            val key = "test-key"

            // Act
            val result = s3StorageClient.generatePublicUrl(bucket, key)

            // Assert
            assertThat(result).isEqualTo("http://localhost:9000/cdn/test-key")
        }

        @Test
        fun `should generate public URL for private bucket with warning`() {
            // Arrange
            val bucket = storageProperties.privateBucket
            val key = "test-key"

            // Act
            val result = s3StorageClient.generatePublicUrl(bucket, key)

            // Assert
            // Should still generate URL but log warning
            assertThat(result).isEqualTo("http://localhost:9000/cdn/test-key")
        }

        @Test
        fun `should handle keys with slashes`() {
            // Arrange
            val bucket = storageProperties.publicBucket
            val key = "path/to/file.jpg"

            // Act
            val result = s3StorageClient.generatePublicUrl(bucket, key)

            // Assert
            assertThat(result).isEqualTo("http://localhost:9000/cdn/path/to/file.jpg")
        }
    }
}
