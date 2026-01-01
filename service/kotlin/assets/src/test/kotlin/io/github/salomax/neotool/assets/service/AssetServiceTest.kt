package io.github.salomax.neotool.assets.service

import io.github.salomax.neotool.assets.config.AssetConfigProperties
import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.domain.AssetVisibility
import io.github.salomax.neotool.assets.entity.AssetEntity
import io.github.salomax.neotool.assets.exception.StorageUnavailableException
import io.github.salomax.neotool.assets.repository.AssetRepository
import io.github.salomax.neotool.assets.storage.BucketResolver
import io.github.salomax.neotool.assets.storage.StorageClient
import io.github.salomax.neotool.assets.storage.StorageKeyFactory
import io.github.salomax.neotool.assets.storage.StorageProperties
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

@DisplayName("AssetService Unit Tests")
class AssetServiceTest {
    private lateinit var assetRepository: AssetRepository
    private lateinit var storageClient: StorageClient
    private lateinit var storageProperties: StorageProperties
    private lateinit var validationService: ValidationService
    private lateinit var assetConfig: AssetConfigProperties
    private lateinit var storageKeyFactory: StorageKeyFactory
    private lateinit var bucketResolver: BucketResolver
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var assetService: AssetService

    @BeforeEach
    fun setUp() {
        assetRepository = mock()
        storageClient = mock()
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
        validationService = mock()
        assetConfig = mock()
        storageKeyFactory = mock()
        bucketResolver = mock()
        meterRegistry = mock()
        assetService =
            AssetService(
                assetRepository,
                storageClient,
                storageProperties,
                validationService,
                assetConfig,
                storageKeyFactory,
                bucketResolver,
                meterRegistry,
            )
    }

    @Nested
    @DisplayName("generateDownloadUrl()")
    inner class GenerateDownloadUrlTests {
        @Test
        fun `should generate download URL for PRIVATE asset`() {
            // Arrange
            val assetId = UUID.randomUUID()
            val requesterId = "user-123"
            val ownerId = "user-123"
            val entity =
                createTestEntity(
                    id = assetId,
                    ownerId = ownerId,
                    visibility = AssetVisibility.PRIVATE,
                    status = AssetStatus.READY,
                )
            val expectedUrl = "https://storage.example.com/presigned-url"

            whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(entity))
            whenever(
                storageClient.generatePresignedDownloadUrl(
                    entity.storageBucket,
                    entity.storageKey,
                    storageProperties.downloadTtlSeconds,
                ),
            )
                .thenReturn(expectedUrl)

            // Act
            val result = assetService.generateDownloadUrl(assetId, requesterId)

            // Assert
            assertThat(result).isEqualTo(expectedUrl)
            verify(assetRepository).findById(assetId)
            verify(storageClient).generatePresignedDownloadUrl(
                entity.storageBucket,
                entity.storageKey,
                storageProperties.downloadTtlSeconds,
            )
        }

        @Test
        fun `should return null for PUBLIC asset`() {
            // Arrange
            val assetId = UUID.randomUUID()
            val requesterId = "user-123"
            val ownerId = "user-123"
            val entity =
                createTestEntity(
                    id = assetId,
                    ownerId = ownerId,
                    visibility = AssetVisibility.PUBLIC,
                    status = AssetStatus.READY,
                )

            whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(entity))

            // Act
            val result = assetService.generateDownloadUrl(assetId, requesterId)

            // Assert
            assertThat(result).isNull()
            verify(assetRepository).findById(assetId)
            verify(storageClient, never()).generatePresignedDownloadUrl(any(), any(), any())
        }

        @Test
        fun `should return null when asset not found`() {
            // Arrange
            val assetId = UUID.randomUUID()
            val requesterId = "user-123"

            whenever(assetRepository.findById(assetId)).thenReturn(Optional.empty())

            // Act
            val result = assetService.generateDownloadUrl(assetId, requesterId)

            // Assert
            assertThat(result).isNull()
            verify(assetRepository).findById(assetId)
            verify(storageClient, never()).generatePresignedDownloadUrl(any(), any(), any())
        }

        @Test
        fun `should return null when unauthorized for PRIVATE asset`() {
            // Arrange
            val assetId = UUID.randomUUID()
            val requesterId = "user-123"
            val ownerId = "user-456" // Different owner
            val entity =
                createTestEntity(
                    id = assetId,
                    ownerId = ownerId,
                    visibility = AssetVisibility.PRIVATE,
                    status = AssetStatus.READY,
                )

            whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(entity))

            // Act
            val result = assetService.generateDownloadUrl(assetId, requesterId)

            // Assert
            assertThat(result).isNull()
            verify(assetRepository).findById(assetId)
            verify(storageClient, never()).generatePresignedDownloadUrl(any(), any(), any())
        }

        @Test
        fun `should cap client TTL to configured maximum`() {
            // Arrange
            val assetId = UUID.randomUUID()
            val requesterId = "user-123"
            val ownerId = "user-123"
            val clientTtlSeconds = 7200L // 2 hours - exceeds default 1 hour
            val entity =
                createTestEntity(
                    id = assetId,
                    ownerId = ownerId,
                    visibility = AssetVisibility.PRIVATE,
                    status = AssetStatus.READY,
                )
            val expectedUrl = "https://storage.example.com/presigned-url"

            whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(entity))
            // Should use capped TTL (3600 seconds, not 7200)
            whenever(
                storageClient.generatePresignedDownloadUrl(
                    entity.storageBucket,
                    entity.storageKey,
                    // Capped to downloadTtlSeconds
                    3600L,
                ),
            )
                .thenReturn(expectedUrl)

            // Act
            val result = assetService.generateDownloadUrl(assetId, requesterId, clientTtlSeconds)

            // Assert
            assertThat(result).isEqualTo(expectedUrl)
            verify(storageClient).generatePresignedDownloadUrl(
                entity.storageBucket,
                entity.storageKey,
                // Should be capped
                3600L,
            )
        }

        @Test
        fun `should use client TTL when less than maximum`() {
            // Arrange
            val assetId = UUID.randomUUID()
            val requesterId = "user-123"
            val ownerId = "user-123"
            val clientTtlSeconds = 1800L // 30 minutes - less than default 1 hour
            val entity =
                createTestEntity(
                    id = assetId,
                    ownerId = ownerId,
                    visibility = AssetVisibility.PRIVATE,
                    status = AssetStatus.READY,
                )
            val expectedUrl = "https://storage.example.com/presigned-url"

            whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(entity))
            whenever(
                storageClient.generatePresignedDownloadUrl(
                    entity.storageBucket,
                    entity.storageKey,
                    // Should use client value
                    clientTtlSeconds,
                ),
            )
                .thenReturn(expectedUrl)

            // Act
            val result = assetService.generateDownloadUrl(assetId, requesterId, clientTtlSeconds)

            // Assert
            assertThat(result).isEqualTo(expectedUrl)
            verify(storageClient).generatePresignedDownloadUrl(
                entity.storageBucket,
                entity.storageKey,
                clientTtlSeconds,
            )
        }
    }

    @Nested
    @DisplayName("confirmUpload()")
    inner class ConfirmUploadTests {
        @Test
        fun `should throw when upload URL has expired`() {
            // Arrange
            val assetId = UUID.randomUUID()
            val ownerId = "user-123"
            val entity =
                createTestEntity(
                    id = assetId,
                    ownerId = ownerId,
                    status = AssetStatus.PENDING,
                    // Expired
                    uploadExpiresAt = Instant.now().minusSeconds(3600),
                )

            whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(entity))
            whenever(assetRepository.update(any())).thenReturn(entity)

            // Act & Assert
            assertThatThrownBy { assetService.confirmUpload(assetId, ownerId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Upload URL has expired")

            verify(assetRepository).findById(assetId)
            verify(assetRepository).update(any()) // Should update status to FAILED
        }

        @Test
        fun `should throw when object does not exist in storage`() {
            // Arrange
            val assetId = UUID.randomUUID()
            val ownerId = "user-123"
            val entity =
                createTestEntity(
                    id = assetId,
                    ownerId = ownerId,
                    status = AssetStatus.PENDING,
                    // Not expired
                    uploadExpiresAt = Instant.now().plusSeconds(3600),
                )

            whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(entity))
            whenever(storageClient.objectExists(entity.storageBucket, entity.storageKey))
                .thenReturn(false)
            whenever(assetRepository.update(any())).thenReturn(entity)

            // Act & Assert
            assertThatThrownBy { assetService.confirmUpload(assetId, ownerId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Upload not completed: file not found in storage")

            verify(assetRepository).findById(assetId)
            verify(storageClient).objectExists(entity.storageBucket, entity.storageKey)
            verify(assetRepository).update(any()) // Updates entity with FAILED status and clears uploadUrl
        }

        @Test
        fun `should throw when metadata retrieval fails`() {
            // Arrange
            val assetId = UUID.randomUUID()
            val ownerId = "user-123"
            val entity =
                createTestEntity(
                    id = assetId,
                    ownerId = ownerId,
                    status = AssetStatus.PENDING,
                    // Not expired
                    uploadExpiresAt = Instant.now().plusSeconds(3600),
                )

            whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(entity))
            whenever(storageClient.objectExists(entity.storageBucket, entity.storageKey))
                .thenReturn(true)
            whenever(storageClient.getObjectMetadata(entity.storageBucket, entity.storageKey))
                .thenReturn(null) // Metadata retrieval fails
            whenever(assetRepository.update(any())).thenReturn(entity)

            // Act & Assert
            assertThatThrownBy { assetService.confirmUpload(assetId, ownerId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Upload verification failed: could not retrieve file metadata")

            verify(assetRepository).findById(assetId)
            verify(storageClient).objectExists(entity.storageBucket, entity.storageKey)
            verify(storageClient).getObjectMetadata(entity.storageBucket, entity.storageKey)
            verify(assetRepository).update(any()) // Updates entity with FAILED status and clears uploadUrl
        }
    }

    @Nested
    @DisplayName("deleteAsset()")
    inner class DeleteAssetTests {
        @Test
        fun `should continue deletion when storage deletion fails`() {
            // Arrange
            val assetId = UUID.randomUUID()
            val ownerId = "user-123"
            val entity =
                createTestEntity(
                    id = assetId,
                    ownerId = ownerId,
                    status = AssetStatus.READY,
                )

            whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(entity))
            whenever(storageClient.deleteObject(entity.storageBucket, entity.storageKey))
                .thenThrow(StorageUnavailableException("Storage unavailable"))
            // Should still delete from database even if storage deletion fails

            // Act
            val result = assetService.deleteAsset(assetId, ownerId)

            // Assert
            assertThat(result).isTrue()
            verify(assetRepository).findById(assetId)
            verify(storageClient).deleteObject(entity.storageBucket, entity.storageKey)
            verify(assetRepository).delete(entity) // Should still delete from DB
        }
    }

    private fun createTestEntity(
        id: UUID = UUID.randomUUID(),
        ownerId: String = "user-123",
        namespace: String = "user-profiles",
        visibility: AssetVisibility = AssetVisibility.PRIVATE,
        status: AssetStatus = AssetStatus.READY,
        storageBucket: String = "test-private-bucket",
        storageKey: String = "test-key",
        uploadExpiresAt: Instant? = null,
    ): AssetEntity {
        return AssetEntity(
            id = id,
            ownerId = ownerId,
            namespace = namespace,
            visibility = visibility,
            storageKey = storageKey,
            storageRegion = "us-east-1",
            storageBucket = storageBucket,
            mimeType = "image/jpeg",
            sizeBytes = 1024L,
            checksum = null,
            originalFilename = "test.jpg",
            uploadUrl = null,
            uploadExpiresAt = uploadExpiresAt,
            publicUrl = null,
            status = status,
            idempotencyKey = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            deletedAt = null,
        )
    }
}
