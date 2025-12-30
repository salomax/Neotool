package io.github.salomax.neotool.assets.test.integration

import io.github.salomax.neotool.assets.domain.Asset
import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.entity.AssetEntity
import io.github.salomax.neotool.assets.repository.AssetRepository
import io.github.salomax.neotool.assets.service.AssetService
import io.github.salomax.neotool.assets.storage.StorageClient
import io.github.salomax.neotool.assets.test.MockStorageClient
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for AssetService.
 * Tests service layer with real database and mocked storage.
 */
@MicronautTest(
    startApplication = true
)
@DisplayName("Asset Service Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("service")
@Tag("assets")
open class AssetServiceIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest, TestPropertyProvider {
    @Inject
    lateinit var assetService: AssetService

    @Inject
    lateinit var assetRepository: AssetRepository

    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    @Named("mockStorageClient")
    lateinit var mockStorageClient: MockStorageClient

    private val testUserId = "test-user-${UUID.randomUUID()}"

    // Override properties to use MockStorageClient for this test
    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties()
        // Enable mock storage client instead of real S3/MinIO
        props["test.use-mock-storage"] = "true"
        return props
    }

    @BeforeEach
    override fun setUp() {
        super.setUp()
        mockStorageClient.clear()
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            assetRepository.deleteAll()
            mockStorageClient.clear()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Initiate Upload")
    inner class InitiateUploadTests {
        @Test
        fun `should create pending asset with upload URL`() {
            // Act
            val asset =
                assetService.initiateUpload(
                    namespace = "user-profiles",
                    ownerId = testUserId,
                    filename = "avatar.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 1048576L,
                )

            // Assert
            Assertions.assertThat(asset.id).isNotNull()
            Assertions.assertThat(asset.status).isEqualTo(AssetStatus.PENDING)
            Assertions.assertThat(asset.uploadUrl).isNotNull()
            Assertions.assertThat(asset.uploadUrl).isNotEmpty()
            Assertions.assertThat(asset.uploadExpiresAt).isNotNull()
            Assertions.assertThat(asset.ownerId).isEqualTo(testUserId)
            Assertions.assertThat(asset.namespace).isEqualTo("user-profiles")

            // Verify persisted in database
            val persisted = assetRepository.findById(asset.id!!)
            Assertions.assertThat(persisted).isPresent
            Assertions.assertThat(persisted.get().status).isEqualTo(AssetStatus.PENDING)
        }

        @Test
        fun `should return existing asset for idempotency key`() {
            // Arrange
            val idempotencyKey = "idempotent-key-${UUID.randomUUID()}"
            val firstAsset =
                assetService.initiateUpload(
                    namespace = "user-profiles",
                    ownerId = testUserId,
                    filename = "avatar.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 1048576L,
                    idempotencyKey = idempotencyKey,
                )

            // Act
            val secondAsset =
                assetService.initiateUpload(
                    namespace = "user-profiles",
                    ownerId = testUserId,
                    filename = "avatar.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 1048576L,
                    idempotencyKey = idempotencyKey,
                )

            // Assert
            Assertions.assertThat(secondAsset.id).isEqualTo(firstAsset.id)
            Assertions.assertThat(assetRepository.count()).isEqualTo(1)
        }

        @Test
        fun `should throw exception for invalid MIME type`() {
            // Act & Assert
            Assertions.assertThatThrownBy {
                assetService.initiateUpload(
                    namespace = "user-profiles",
                    ownerId = testUserId,
                    filename = "document.pdf",
                    mimeType = "application/pdf", // Not allowed for user-profiles
                    sizeBytes = 1048576L,
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should throw exception for file size exceeding limit`() {
            // Act & Assert
            Assertions.assertThatThrownBy {
                assetService.initiateUpload(
                    namespace = "user-profiles",
                    ownerId = testUserId,
                    filename = "large.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 100_000_000L, // Exceeds default limit
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    @DisplayName("Confirm Upload")
    inner class ConfirmUploadTests {
        @Test
        fun `should confirm upload and mark asset as ready`() {
            // Arrange
            val asset =
                assetService.initiateUpload(
                    namespace = "user-profiles",
                    ownerId = testUserId,
                    filename = "avatar.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 1024L,
                )
            mockStorageClient.simulateUpload(asset.storageKey!!, sizeBytes = 1024L, contentType = "image/jpeg")

            // Act
            val confirmedAsset = assetService.confirmUpload(asset.id!!, testUserId)

            // Assert
            Assertions.assertThat(confirmedAsset.status).isEqualTo(AssetStatus.READY)
            Assertions.assertThat(confirmedAsset.sizeBytes).isEqualTo(1024L)
            // publicUrl is now generated dynamically from storageKey, not stored
            Assertions.assertThat(confirmedAsset.storageKey).isNotEmpty()
            // Verify publicUrl can be generated
            val generatedPublicUrl = Asset.generatePublicUrl("https://cdn.example.com", confirmedAsset.storageKey)
            Assertions.assertThat(generatedPublicUrl).isNotEmpty()

            // Verify persisted
            val persisted = assetRepository.findById(asset.id!!)
            Assertions.assertThat(persisted).isPresent
            Assertions.assertThat(persisted.get().status).isEqualTo(AssetStatus.READY)
        }

        @Test
        fun `should throw exception when object does not exist in storage`() {
            // Arrange
            val asset =
                assetService.initiateUpload(
                    namespace = "user-profiles",
                    ownerId = testUserId,
                    filename = "avatar.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 1024L,
                )
            // Don't simulate upload - object doesn't exist

            // Act & Assert
            Assertions.assertThatThrownBy {
                assetService.confirmUpload(asset.id!!, testUserId)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("not found in storage")

            // Verify asset is marked as FAILED and uploadUrl is cleared
            val persisted = assetRepository.findById(asset.id!!)
            Assertions.assertThat(persisted).isPresent
            Assertions.assertThat(persisted.get().status).isEqualTo(AssetStatus.FAILED)
            Assertions.assertThat(persisted.get().uploadUrl).isNull()
        }

        @Test
        fun `should throw exception when asset is not pending`() {
            // Arrange
            val asset =
                entityManager.runTransaction {
                    val entity =
                        AssetEntity(
                            id = null,
                            ownerId = testUserId,
                            namespace = "user-profiles",
                            storageKey = "test/key",
                            storageRegion = "us-east-1",
                            storageBucket = "test-bucket",
                            mimeType = "image/jpeg",
                            sizeBytes = 1024L,
                            checksum = null,
                            originalFilename = "test.jpg",
                            uploadUrl = null,
                            uploadExpiresAt = null,
                            publicUrl = null, // No longer stored - generated dynamically
                            status = AssetStatus.READY, // Already ready
                            idempotencyKey = null,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                            deletedAt = null,
                        )
                    assetRepository.save(entity)
                }

            // Act & Assert
            Assertions.assertThatThrownBy {
                assetService.confirmUpload(asset.id!!, testUserId)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("not in PENDING status")
        }
    }

    @Nested
    @DisplayName("Get Asset")
    inner class GetAssetTests {
        @Test
        fun `should return asset for owner`() {
            // Arrange
            val asset =
                entityManager.runTransaction {
                    val entity =
                        AssetEntity(
                            id = null,
                            ownerId = testUserId,
                            namespace = "user-profiles",
                            storageKey = "test/key",
                            storageRegion = "us-east-1",
                            storageBucket = "test-bucket",
                            mimeType = "image/jpeg",
                            sizeBytes = 1024L,
                            checksum = null,
                            originalFilename = "test.jpg",
                            uploadUrl = null,
                            uploadExpiresAt = null,
                            publicUrl = null, // No longer stored - generated dynamically
                            status = AssetStatus.READY,
                            idempotencyKey = null,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                            deletedAt = null,
                        )
                    assetRepository.save(entity)
                }

            // Act
            val retrieved = assetService.getAsset(asset.id!!, testUserId)

            // Assert
            Assertions.assertThat(retrieved).isNotNull()
            Assertions.assertThat(retrieved?.id).isEqualTo(asset.id)
            Assertions.assertThat(retrieved?.ownerId).isEqualTo(testUserId)
        }

        @Test
        fun `should return null for non-existent asset`() {
            // Act
            val retrieved = assetService.getAsset(UUID.randomUUID(), testUserId)

            // Assert
            Assertions.assertThat(retrieved).isNull()
        }

        @Test
        fun `should return null for asset owned by different user`() {
            // Arrange
            val otherUserId = "other-user-${UUID.randomUUID()}"
            val asset =
                entityManager.runTransaction {
                    val entity =
                        AssetEntity(
                            id = null,
                            ownerId = otherUserId,
                            namespace = "user-profiles",
                            storageKey = "test/key",
                            storageRegion = "us-east-1",
                            storageBucket = "test-bucket",
                            mimeType = "image/jpeg",
                            sizeBytes = 1024L,
                            checksum = null,
                            originalFilename = "test.jpg",
                            uploadUrl = null,
                            uploadExpiresAt = null,
                            publicUrl = null, // No longer stored - generated dynamically
                            status = AssetStatus.READY,
                            idempotencyKey = null,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                            deletedAt = null,
                        )
                    assetRepository.save(entity)
                }

            // Act
            val retrieved = assetService.getAsset(asset.id!!, testUserId)

            // Assert
            Assertions.assertThat(retrieved).isNull()
        }
    }

    @Nested
    @DisplayName("Delete Asset")
    inner class DeleteAssetTests {
        @Test
        fun `should hard delete asset`() {
            // Arrange
            val asset =
                entityManager.runTransaction {
                    val entity =
                        AssetEntity(
                            id = null,
                            ownerId = testUserId,
                            namespace = "user-profiles",
                            storageKey = "test/key",
                            storageRegion = "us-east-1",
                            storageBucket = "test-bucket",
                            mimeType = "image/jpeg",
                            sizeBytes = 1024L,
                            checksum = null,
                            originalFilename = "test.jpg",
                            uploadUrl = null,
                            uploadExpiresAt = null,
                            publicUrl = null, // No longer stored - generated dynamically
                            status = AssetStatus.READY,
                            idempotencyKey = null,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                            deletedAt = null,
                        )
                    assetRepository.save(entity)
                }
            mockStorageClient.simulateUpload(asset.storageKey, sizeBytes = 1024L)

            // Act
            val deleted = assetService.deleteAsset(asset.id!!, testUserId)

            // Assert
            Assertions.assertThat(deleted).isTrue()

            // Verify asset is hard-deleted (should not exist)
            val persisted = assetRepository.findById(asset.id!!)
            Assertions.assertThat(persisted).isEmpty
        }

        @Test
        fun `should return false for non-existent asset`() {
            // Act
            val deleted = assetService.deleteAsset(UUID.randomUUID(), testUserId)

            // Assert
            Assertions.assertThat(deleted).isFalse()
        }

        @Test
        fun `should return false for asset owned by different user`() {
            // Arrange
            val otherUserId = "other-user-${UUID.randomUUID()}"
            val asset =
                entityManager.runTransaction {
                    val entity =
                        AssetEntity(
                            id = null,
                            ownerId = otherUserId,
                            namespace = "user-profiles",
                            storageKey = "test/key",
                            storageRegion = "us-east-1",
                            storageBucket = "test-bucket",
                            mimeType = "image/jpeg",
                            sizeBytes = 1024L,
                            checksum = null,
                            originalFilename = "test.jpg",
                            uploadUrl = null,
                            uploadExpiresAt = null,
                            publicUrl = null, // No longer stored - generated dynamically
                            status = AssetStatus.READY,
                            idempotencyKey = null,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                            deletedAt = null,
                        )
                    assetRepository.save(entity)
                }

            // Act
            val deleted = assetService.deleteAsset(asset.id!!, testUserId)

            // Assert
            Assertions.assertThat(deleted).isFalse()
        }
    }

}

