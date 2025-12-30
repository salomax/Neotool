package io.github.salomax.neotool.assets.test.integration

import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.repository.AssetRepository
import io.github.salomax.neotool.assets.service.AssetService
import io.github.salomax.neotool.assets.storage.StorageClient
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.MinIOIntegrationTest
import io.github.salomax.neotool.common.test.integration.MinIOTestContainer
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import java.net.URI
import java.util.UUID

/**
 * Integration tests for AssetService with real MinIO storage.
 * Tests full upload flow: createAssetUpload -> PUT to MinIO -> confirmAssetUpload.
 */
@MicronautTest(
    startApplication = true,
)
@DisplayName("Asset Service MinIO Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("minio")
@Tag("assets")
open class AssetServiceMinIOIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest, MinIOIntegrationTest {
    @Inject
    lateinit var assetService: AssetService

    @Inject
    lateinit var assetRepository: AssetRepository

    @Inject
    lateinit var storageClient: StorageClient

    private val testUserId = "test-user-${UUID.randomUUID()}"
    private lateinit var s3Client: S3Client

    @BeforeEach
    override fun setUp() {
        super.setUp()

        // Create S3 client for bucket setup
        val minioContainer = MinIOTestContainer.container
        val endpoint = MinIOTestContainer.getApiEndpoint()
        val credentials = AwsBasicCredentials.create("minioadmin", "minioadmin")

        s3Client =
            S3Client
                .builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("us-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(
                    software.amazon.awssdk.services.s3.S3Configuration
                        .builder()
                        .pathStyleAccessEnabled(true)
                        .build(),
                )
                .build()

        // Ensure both buckets exist
        val publicBucket = "neotool-assets-public"
        val privateBucket = "neotool-assets-private"
        
        listOf(publicBucket, privateBucket).forEach { bucketName ->
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build())
            } catch (e: NoSuchBucketException) {
                // Bucket doesn't exist, create it
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build())
            }
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            assetRepository.deleteAll()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Full Upload Flow with MinIO")
    inner class FullUploadFlowTests {
        @Test
        fun `should complete full upload flow with real MinIO`() {
            // Arrange
            val namespace = "user-profiles"
            val filename = "avatar.jpg"
            val mimeType = "image/jpeg"
            val sizeBytes = 1024L

            // Act - Step 1: Create upload
            val asset =
                assetService.initiateUpload(
                    namespace = namespace,
                    ownerId = testUserId,
                    filename = filename,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                )

            // Assert - Step 1
            Assertions.assertThat(asset.id).isNotNull()
            Assertions.assertThat(asset.status).isEqualTo(AssetStatus.PENDING)
            Assertions.assertThat(asset.uploadUrl).isNotNull()
            Assertions.assertThat(asset.uploadUrl).isNotEmpty()
            Assertions.assertThat(asset.storageKey).isNotNull()

            // Act - Step 2: Upload file to MinIO using presigned URL
            val uploadUrl = asset.uploadUrl!!
            val testFileContent = ByteArray(sizeBytes.toInt()) { it.toByte() }
            val response =
                java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest
                        .newBuilder(URI.create(uploadUrl))
                        .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(testFileContent))
                        .header("Content-Type", mimeType)
                        .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString(),
                )

            // Assert - Step 2: Upload should succeed
            Assertions.assertThat(response.statusCode()).isIn(200, 204)

            // Act - Step 3: Confirm upload
            val confirmedAsset = assetService.confirmUpload(asset.id!!, testUserId)

            // Assert - Step 3
            Assertions.assertThat(confirmedAsset.status).isEqualTo(AssetStatus.READY)
            Assertions.assertThat(confirmedAsset.sizeBytes).isEqualTo(sizeBytes)
            Assertions.assertThat(confirmedAsset.storageKey).isEqualTo(asset.storageKey)

            // Verify object exists in MinIO
            val objectExists = storageClient.objectExists(confirmedAsset.storageBucket, confirmedAsset.storageKey!!)
            Assertions.assertThat(objectExists).isTrue()

            // Verify metadata
            val metadata = storageClient.getObjectMetadata(confirmedAsset.storageBucket, confirmedAsset.storageKey!!)
            Assertions.assertThat(metadata).isNotNull()
            Assertions.assertThat(metadata?.sizeBytes).isEqualTo(sizeBytes)
        }

        @Test
        fun `should verify presigned URL works with MinIO`() {
            // Arrange
            val asset =
                assetService.initiateUpload(
                    namespace = "user-profiles",
                    ownerId = testUserId,
                    filename = "test.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 512L,
                )

            // Act
            val uploadUrl = asset.uploadUrl!!
            val testContent = "test file content".toByteArray()

            // Upload to presigned URL
            val response =
                java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest
                        .newBuilder(URI.create(uploadUrl))
                        .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(testContent))
                        .header("Content-Type", "image/jpeg")
                        .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString(),
                )

            // Assert
            Assertions.assertThat(response.statusCode()).isIn(200, 204)

            // Verify object exists
            val exists = storageClient.objectExists(asset.storageBucket, asset.storageKey!!)
            Assertions.assertThat(exists).isTrue()
        }

        @Test
        fun `should handle object deletion with MinIO`() {
            // Arrange - Create and confirm an asset
            val asset =
                assetService.initiateUpload(
                    namespace = "user-profiles",
                    ownerId = testUserId,
                    filename = "delete-test.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 256L,
                )

            // Upload file
            val uploadUrl = asset.uploadUrl!!
            val testContent = "test".toByteArray()
            java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest
                    .newBuilder(URI.create(uploadUrl))
                    .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(testContent))
                    .header("Content-Type", "image/jpeg")
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            )

            // Confirm upload
            val confirmedAsset = assetService.confirmUpload(asset.id!!, testUserId)
            Assertions.assertThat(storageClient.objectExists(confirmedAsset.storageBucket, confirmedAsset.storageKey!!)).isTrue()

            // Act - Delete asset
            val deleted = assetService.deleteAsset(confirmedAsset.id!!, testUserId)

            // Assert
            Assertions.assertThat(deleted).isTrue()

            // Verify object is deleted from MinIO
            val stillExists = storageClient.objectExists(confirmedAsset.storageBucket, confirmedAsset.storageKey!!)
            Assertions.assertThat(stillExists).isFalse()

            // Verify metadata is hard-deleted (should not exist)
            val persisted = assetRepository.findById(confirmedAsset.id!!)
            Assertions.assertThat(persisted).isEmpty
        }

        @Test
        fun `should verify object existence checks with MinIO`() {
            // Arrange
            val asset =
                assetService.initiateUpload(
                    namespace = "user-profiles",
                    ownerId = testUserId,
                    filename = "exists-test.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 128L,
                )

            // Act - Check before upload
            val existsBefore = storageClient.objectExists(asset.storageBucket, asset.storageKey!!)
            Assertions.assertThat(existsBefore).isFalse()

            // Upload file
            val uploadUrl = asset.uploadUrl!!
            val testContent = "test content".toByteArray()
            java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest
                    .newBuilder(URI.create(uploadUrl))
                    .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(testContent))
                    .header("Content-Type", "image/jpeg")
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            )

            // Act - Check after upload
            val existsAfter = storageClient.objectExists(asset.storageBucket, asset.storageKey!!)
            Assertions.assertThat(existsAfter).isTrue()
        }

        @Test
        fun `should retrieve object metadata from MinIO`() {
            // Arrange
            val sizeBytes = 256L
            val asset =
                assetService.initiateUpload(
                    namespace = "user-profiles",
                    ownerId = testUserId,
                    filename = "metadata-test.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = sizeBytes,
                )

            // Upload file
            val uploadUrl = asset.uploadUrl!!
            val testContent = ByteArray(sizeBytes.toInt()) { it.toByte() }
            java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest
                    .newBuilder(URI.create(uploadUrl))
                    .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(testContent))
                    .header("Content-Type", "image/jpeg")
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            )

            // Act
            val metadata = storageClient.getObjectMetadata(asset.storageBucket, asset.storageKey!!)

            // Assert
            Assertions.assertThat(metadata).isNotNull()
            Assertions.assertThat(metadata?.sizeBytes).isEqualTo(sizeBytes)
            Assertions.assertThat(metadata?.contentType).isEqualTo("image/jpeg")
            Assertions.assertThat(metadata?.etag).isNotNull()
        }
    }
}
