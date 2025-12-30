package io.github.salomax.neotool.assets.test.integration

import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.entity.AssetEntity
import io.github.salomax.neotool.assets.repository.AssetRepository
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for AssetRepository.
 * Tests database operations with real PostgreSQL database.
 */
@MicronautTest(startApplication = true)
@DisplayName("Asset Repository Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("repository")
@Tag("assets")
open class AssetRepositoryIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var assetRepository: AssetRepository

    @Inject
    lateinit var entityManager: EntityManager

    @AfterEach
    fun cleanupTestData() {
        try {
            assetRepository.deleteAll()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun createTestAsset(
        ownerId: String = "test-user-${UUID.randomUUID()}",
        status: AssetStatus = AssetStatus.READY,
        namespace: String = "user-profiles",
    ): AssetEntity {
        return entityManager.runTransaction {
            val entity =
                AssetEntity(
                    id = null,
                    ownerId = ownerId,
                    namespace = namespace,
                    storageKey = "$namespace/$ownerId/${UUID.randomUUID()}",
                    storageRegion = "us-east-1",
                    storageBucket = "test-bucket",
                    mimeType = "image/jpeg",
                    sizeBytes = 1024L,
                    checksum = "test-checksum",
                    originalFilename = "test.jpg",
                    uploadUrl = if (status == AssetStatus.PENDING) "https://upload.example.com/presigned" else null,
                    uploadExpiresAt = if (status == AssetStatus.PENDING) Instant.now().plusSeconds(900) else null,
                    publicUrl = null, // No longer stored - generated dynamically
                    status = status,
                    idempotencyKey = null,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    deletedAt = null,
                )
            val saved = assetRepository.save(entity)
            entityManager.flush()
            saved
        }
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    inner class BasicCrudTests {
        @Test
        fun `should save and retrieve asset`() {
            // Arrange
            val entity =
                AssetEntity(
                    id = null,
                    ownerId = "test-user",
                    namespace = "user-profiles",
                    storageKey = "user-profiles/profile-image/test-user/${UUID.randomUUID()}",
                    storageRegion = "us-east-1",
                    storageBucket = "test-bucket",
                    mimeType = "image/jpeg",
                    sizeBytes = 1024L,
                    checksum = "test-checksum",
                    originalFilename = "avatar.jpg",
                    uploadUrl = null,
                    uploadExpiresAt = null,
                    publicUrl = null, // No longer stored - generated dynamically
                    status = AssetStatus.READY,
                    idempotencyKey = null,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    deletedAt = null,
                )

            // Act
            val saved = entityManager.runTransaction {
                val result = assetRepository.save(entity)
                entityManager.flush()
                result
            }

            // Assert
            Assertions.assertThat(saved.id).isNotNull()
            val retrieved = assetRepository.findById(saved.id!!)
            Assertions.assertThat(retrieved).isPresent
            Assertions.assertThat(retrieved.get().ownerId).isEqualTo("test-user")
            Assertions.assertThat(retrieved.get().namespace).isEqualTo("user-profiles")
            Assertions.assertThat(retrieved.get().status).isEqualTo(AssetStatus.READY)
        }

        @Test
        fun `should update asset`() {
            // Arrange
            val asset = createTestAsset()

            // Act
            entityManager.runTransaction {
                asset.status = AssetStatus.DELETED
                asset.deletedAt = Instant.now()
                assetRepository.update(asset)
                entityManager.flush()
            }

            // Assert
            val updated = assetRepository.findById(asset.id!!)
            Assertions.assertThat(updated).isPresent
            Assertions.assertThat(updated.get().status).isEqualTo(AssetStatus.DELETED)
            Assertions.assertThat(updated.get().deletedAt).isNotNull()
        }

        @Test
        fun `should delete asset`() {
            // Arrange
            val asset = createTestAsset()

            // Act
            entityManager.runTransaction {
                assetRepository.deleteById(asset.id!!)
                entityManager.flush()
            }

            // Assert
            val deleted = assetRepository.findById(asset.id!!)
            Assertions.assertThat(deleted).isEmpty
        }
    }

    @Nested
    @DisplayName("Find By Storage Key")
    inner class FindByStorageKeyTests {
        @Test
        fun `should find asset by storage key`() {
            // Arrange
            val asset = createTestAsset()
            val storageKey = asset.storageKey

            // Act
            val found = assetRepository.findByStorageKey(storageKey)

            // Assert
            Assertions.assertThat(found).isPresent
            Assertions.assertThat(found.get().id).isEqualTo(asset.id)
            Assertions.assertThat(found.get().storageKey).isEqualTo(storageKey)
        }

        @Test
        fun `should return empty for non-existent storage key`() {
            // Act
            val found = assetRepository.findByStorageKey("non-existent-key")

            // Assert
            Assertions.assertThat(found).isEmpty
        }
    }

    @Nested
    @DisplayName("Find By Owner")
    inner class FindByOwnerTests {
        @Test
        fun `should find assets by owner ID`() {
            // Arrange
            val ownerId = "owner-${UUID.randomUUID()}"
            val asset1 = createTestAsset(ownerId = ownerId)
            val asset2 = createTestAsset(ownerId = ownerId)
            createTestAsset(ownerId = "other-owner") // Should not be returned

            // Act
            val assets = assetRepository.findByOwnerId(ownerId)

            // Assert
            Assertions.assertThat(assets).hasSizeGreaterThanOrEqualTo(2)
            val assetIds = assets.map { it.id }.toSet()
            Assertions.assertThat(assetIds).contains(asset1.id, asset2.id)
        }

        @Test
        fun `should find assets by owner ID and status`() {
            // Arrange
            val ownerId = "owner-${UUID.randomUUID()}"
            val asset1 = createTestAsset(ownerId = ownerId, status = AssetStatus.READY)
            val asset2 = createTestAsset(ownerId = ownerId, status = AssetStatus.PENDING)
            createTestAsset(ownerId = ownerId, status = AssetStatus.DELETED) // Should not be returned

            // Act
            val assets = assetRepository.findByOwnerIdAndStatus(ownerId, AssetStatus.READY)

            // Assert
            Assertions.assertThat(assets).hasSizeGreaterThanOrEqualTo(1)
            val assetIds = assets.map { it.id }.toSet()
            Assertions.assertThat(assetIds).contains(asset1.id)
            Assertions.assertThat(assetIds).doesNotContain(asset2.id)
        }
    }

    @Nested
    @DisplayName("Find By Namespace")
    inner class FindByNamespaceTests {
        @Test
        fun `should find assets by namespace`() {
            // Arrange
            val namespace = "namespace-${UUID.randomUUID()}"
            val asset1 = createTestAsset(namespace = namespace)
            val asset2 = createTestAsset(namespace = namespace)
            createTestAsset(namespace = "other-namespace") // Should not be returned

            // Act
            val assets = assetRepository.findByNamespace(namespace)

            // Assert
            Assertions.assertThat(assets).hasSizeGreaterThanOrEqualTo(2)
            val assetIds = assets.map { it.id }.toSet()
            Assertions.assertThat(assetIds).contains(asset1.id, asset2.id)
        }

        @Test
        fun `should find assets by namespace and status`() {
            // Arrange
            val namespace = "namespace-${UUID.randomUUID()}"
            val asset1 = createTestAsset(namespace = namespace, status = AssetStatus.READY)
            val asset2 = createTestAsset(namespace = namespace, status = AssetStatus.PENDING)
            createTestAsset(namespace = namespace, status = AssetStatus.DELETED) // Should not be returned

            // Act
            val assets = assetRepository.findByNamespaceAndStatus(namespace, AssetStatus.READY)

            // Assert
            Assertions.assertThat(assets).hasSizeGreaterThanOrEqualTo(1)
            val assetIds = assets.map { it.id }.toSet()
            Assertions.assertThat(assetIds).contains(asset1.id)
            Assertions.assertThat(assetIds).doesNotContain(asset2.id)
        }
    }

    @Nested
    @DisplayName("Find By Idempotency Key")
    inner class FindByIdempotencyKeyTests {
        @Test
        fun `should find asset by owner ID and idempotency key`() {
            // Arrange
            val ownerId = "owner-${UUID.randomUUID()}"
            val idempotencyKey = "idempotent-key-${UUID.randomUUID()}"
            val asset =
                entityManager.runTransaction {
                    val entity =
                        AssetEntity(
                            id = null,
                            ownerId = ownerId,
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
                            idempotencyKey = idempotencyKey,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                            deletedAt = null,
                        )
                    assetRepository.save(entity)
                }

            // Act
            val found = assetRepository.findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey)

            // Assert
            Assertions.assertThat(found).isPresent
            Assertions.assertThat(found.get().id).isEqualTo(asset.id)
            Assertions.assertThat(found.get().idempotencyKey).isEqualTo(idempotencyKey)
        }

        @Test
        fun `should return empty for non-existent idempotency key`() {
            // Act
            val found = assetRepository.findByOwnerIdAndIdempotencyKey("owner-123", "non-existent-key")

            // Assert
            Assertions.assertThat(found).isEmpty
        }
    }

    @Nested
    @DisplayName("Count Operations")
    inner class CountOperationsTests {
        @Test
        fun `should count assets by owner and status`() {
            // Arrange
            val ownerId = "owner-${UUID.randomUUID()}"
            createTestAsset(ownerId = ownerId, status = AssetStatus.READY)
            createTestAsset(ownerId = ownerId, status = AssetStatus.READY)
            createTestAsset(ownerId = ownerId, status = AssetStatus.PENDING)
            createTestAsset(ownerId = "other-owner", status = AssetStatus.READY) // Should not be counted

            // Act
            val count = assetRepository.countByOwnerIdAndStatus(ownerId, AssetStatus.READY)

            // Assert
            Assertions.assertThat(count).isGreaterThanOrEqualTo(2)
        }

        @Test
        fun `should count assets by namespace and status`() {
            // Arrange
            val namespace = "namespace-${UUID.randomUUID()}"
            createTestAsset(namespace = namespace, status = AssetStatus.READY)
            createTestAsset(namespace = namespace, status = AssetStatus.READY)
            createTestAsset(namespace = namespace, status = AssetStatus.PENDING)
            createTestAsset(namespace = "other-namespace", status = AssetStatus.READY) // Should not be counted

            // Act
            val count = assetRepository.countByNamespaceAndStatus(namespace, AssetStatus.READY)

            // Assert
            Assertions.assertThat(count).isGreaterThanOrEqualTo(2)
        }
    }

    @Nested
    @DisplayName("Sum Operations")
    inner class SumOperationsTests {
        @Test
        fun `should sum file sizes by owner ID`() {
            // Arrange
            val ownerId = "owner-${UUID.randomUUID()}"
            createTestAsset(ownerId = ownerId, status = AssetStatus.READY)
            createTestAsset(ownerId = ownerId, status = AssetStatus.READY)
            createTestAsset(ownerId = ownerId, status = AssetStatus.PENDING) // Should not be counted
            createTestAsset(ownerId = "other-owner", status = AssetStatus.READY) // Should not be counted

            // Act
            val sum = assetRepository.sumFileSizeByOwnerId(ownerId)

            // Assert
            Assertions.assertThat(sum).isGreaterThanOrEqualTo(2048L) // At least 2 assets * 1024L
        }

        @Test
        fun `should return zero for owner with no assets`() {
            // Act
            val sum = assetRepository.sumFileSizeByOwnerId("non-existent-owner")

            // Assert
            Assertions.assertThat(sum).isEqualTo(0L)
        }
    }
}

