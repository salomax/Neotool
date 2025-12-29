package io.github.salomax.neotool.assets.test.integration

import io.github.salomax.neotool.assets.domain.Asset
import io.github.salomax.neotool.assets.domain.AssetResourceType
import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.entity.AssetEntity
import io.github.salomax.neotool.assets.repository.AssetRepository
import io.github.salomax.neotool.assets.storage.StorageClient
import io.github.salomax.neotool.assets.test.MockStorageClient
import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.assertions.shouldHaveNonEmptyBody
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.json.read
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import java.util.Optional
import io.micronaut.json.tree.JsonNode
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
 * Integration tests for Asset GraphQL operations.
 * Tests cover all GraphQL queries and mutations with real database and mocked storage.
 */
@MicronautTest(
    startApplication = true
)
@DisplayName("Asset GraphQL Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("assets")
open class AssetGraphQLIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest, TestPropertyProvider {
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

    private fun createTestAsset(
        ownerId: String = testUserId,
        status: AssetStatus = AssetStatus.READY,
        namespace: String = "user-profiles",
        resourceType: AssetResourceType = AssetResourceType.PROFILE_IMAGE,
        resourceId: String = "resource-123",
    ): AssetEntity {
        val entity =
            AssetEntity(
                id = null,
                ownerId = ownerId,
                namespace = namespace,
                resourceType = resourceType,
                resourceId = resourceId,
                storageKey = Asset.generateStorageKey(
                    namespace,
                    resourceType,
                    resourceId,
                    UUID.randomUUID()
                ),
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

        return entityManager.runTransaction {
            val saved = assetRepository.save(entity)
            entityManager.flush()
            saved
        }
    }

    @Nested
    @DisplayName("Asset Query Tests")
    inner class AssetQueryTests {
        @Test
        fun `should query asset by ID`() {
            // Arrange
            val asset = createTestAsset()

            val query =
                mapOf(
                    "query" to
                        """
                        query GetAsset(${'$'}id: ID!) {
                            asset(id: ${'$'}id) {
                                id
                                ownerId
                                namespace
                                resourceType
                                resourceId
                                status
                                mimeType
                                sizeBytes
                                publicUrl
                            }
                        }
                        """.trimIndent(),
                    "variables" to mapOf("id" to asset.id.toString()),
                )

            // Act
            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token")

            // Get response - GraphQL returns 200 even with errors, so we can use exchange
            val response = try {
                httpClient.exchangeAsString(request)
            } catch (e: io.micronaut.http.client.exceptions.HttpClientResponseException) {
                // If exchange throws, try to read the error response
                val errorResponse = e.response
                val errorBody = if (errorResponse != null) {
                    try {
                        // Try to parse as JSON to get GraphQL errors
                        val errorPayload: JsonNode = json.read(errorResponse)
                        if (errorPayload["errors"] != null && !errorPayload["errors"].isNull) {
                            (0 until errorPayload["errors"].size()).joinToString("\n") { i ->
                                val error = errorPayload["errors"][i]
                                val message = error["message"]?.stringValue ?: "No message"
                                val path = error["path"]?.toString() ?: "No path"
                                "Error $i: $message (path: $path)"
                            }
                        } else {
                            e.message ?: "Unknown error"
                        }
                    } catch (ex: Exception) {
                        e.message ?: "Unknown error"
                    }
                } else {
                    e.message ?: "Unknown error"
                }
                throw AssertionError("HTTP request failed (${e.status}):\n$errorBody", e)
            }

            // Assert
            // Check for errors first to get better error messages
            val payload: JsonNode = json.read(response)
            if (payload["errors"] != null && !payload["errors"].isNull && payload["errors"].size() > 0) {
                val errorMsg = (0 until payload["errors"].size()).joinToString("\n") { i ->
                    val error = payload["errors"][i]
                    val message = error["message"]?.stringValue ?: "No message"
                    val path = error["path"]?.toString() ?: "No path"
                    "Error $i: $message (path: $path)"
                }
                throw AssertionError("GraphQL errors:\n$errorMsg\nResponse: $response")
            }
            
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            payload["errors"].assertNoErrors()

            val data = payload["data"]
            Assertions.assertThat(data).isNotNull()

            val assetData = data["asset"]
            Assertions.assertThat(assetData).isNotNull()
            Assertions.assertThat(assetData["id"].stringValue).isEqualTo(asset.id.toString())
            Assertions.assertThat(assetData["ownerId"].stringValue).isEqualTo(testUserId)
            Assertions.assertThat(assetData["namespace"].stringValue).isEqualTo("user-profiles")
            Assertions.assertThat(assetData["resourceType"].stringValue).isEqualTo("PROFILE_IMAGE")
            Assertions.assertThat(assetData["status"].stringValue).isEqualTo("READY")
            Assertions.assertThat(assetData["mimeType"].stringValue).isEqualTo("image/jpeg")
            Assertions.assertThat(assetData["sizeBytes"].longValue).isEqualTo(1024L)
            // publicUrl is now generated dynamically, just verify it's present and not empty
            Assertions.assertThat(assetData["publicUrl"].stringValue).isNotEmpty()
        }

        @Test
        fun `should return null for non-existent asset`() {
            // Arrange
            val nonExistentId = UUID.randomUUID()

            val query =
                mapOf(
                    "query" to
                        """
                        query GetAsset(${'$'}id: ID!) {
                            asset(id: ${'$'}id) {
                                id
                            }
                        }
                        """.trimIndent(),
                    "variables" to mapOf("id" to nonExistentId.toString()),
                )

            // Act
            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token")

            val response = httpClient.exchangeAsString(request)

            // Assert
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            Assertions.assertThat(data["asset"].isNull).isTrue()
        }

        @Test
        fun `should query assets by resource`() {
            // Arrange
            val resourceId = "resource-${UUID.randomUUID()}"
            val asset1 = createTestAsset(resourceId = resourceId, resourceType = AssetResourceType.PROFILE_IMAGE)
            val asset2 = createTestAsset(resourceId = resourceId, resourceType = AssetResourceType.COVER_IMAGE)
            createTestAsset(resourceId = "other-resource") // Should not be returned

            val query =
                mapOf(
                    "query" to
                        """
                        query GetAssetsByResource(${'$'}resourceType: AssetResourceType!, ${'$'}resourceId: String!) {
                            assetsByResource(resourceType: ${'$'}resourceType, resourceId: ${'$'}resourceId) {
                                id
                                resourceType
                                resourceId
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "resourceType" to "PROFILE_IMAGE",
                            "resourceId" to resourceId,
                        ),
                )

            // Act
            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token")

            val response = httpClient.exchangeAsString(request)

            // Assert
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            val assets = data["assetsByResource"]
            Assertions.assertThat(assets.isArray).isTrue()
            Assertions.assertThat(assets.size()).isGreaterThanOrEqualTo(1)

            val assetIds = (0 until assets.size()).map { assets[it]["id"].stringValue }.toSet()
            Assertions.assertThat(assetIds).contains(asset1.id.toString())
        }

        @Test
        fun `should query assets by owner`() {
            // Arrange
            val ownerId = "owner-${UUID.randomUUID()}"
            val asset1 = createTestAsset(ownerId = ownerId, status = AssetStatus.READY)
            val asset2 = createTestAsset(ownerId = ownerId, status = AssetStatus.PENDING)
            createTestAsset(ownerId = "other-owner") // Should not be returned

            val query =
                mapOf(
                    "query" to
                        """
                        query GetAssetsByOwner(${'$'}ownerId: String!, ${'$'}status: AssetStatus) {
                            assetsByOwner(ownerId: ${'$'}ownerId, status: ${'$'}status) {
                                id
                                ownerId
                                status
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "ownerId" to ownerId,
                            "status" to "READY",
                        ),
                )

            // Act
            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token")

            val response = httpClient.exchangeAsString(request)

            // Assert
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            val assets = data["assetsByOwner"]
            Assertions.assertThat(assets.isArray).isTrue()
            Assertions.assertThat(assets.size()).isGreaterThanOrEqualTo(1)

            val assetIds = (0 until assets.size()).map { assets[it]["id"].stringValue }.toSet()
            Assertions.assertThat(assetIds).contains(asset1.id.toString())
            Assertions.assertThat(assetIds).doesNotContain(asset2.id.toString())
        }

        @Test
        fun `should query assets by namespace`() {
            // Arrange
            val namespace = "test-namespace-${UUID.randomUUID()}"
            val asset1 = createTestAsset(namespace = namespace, status = AssetStatus.READY)
            val asset2 = createTestAsset(namespace = namespace, status = AssetStatus.PENDING)
            createTestAsset(namespace = "other-namespace") // Should not be returned

            val query =
                mapOf(
                    "query" to
                        """
                        query GetAssetsByNamespace(${'$'}namespace: String!, ${'$'}status: AssetStatus) {
                            assetsByNamespace(namespace: ${'$'}namespace, status: ${'$'}status) {
                                id
                                namespace
                                status
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "namespace" to namespace,
                            "status" to "READY",
                        ),
                )

            // Act
            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token")

            val response = httpClient.exchangeAsString(request)

            // Assert
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            val assets = data["assetsByNamespace"]
            Assertions.assertThat(assets.isArray).isTrue()
            Assertions.assertThat(assets.size()).isGreaterThanOrEqualTo(1)

            val assetIds = (0 until assets.size()).map { assets[it]["id"].stringValue }.toSet()
            Assertions.assertThat(assetIds).contains(asset1.id.toString())
            Assertions.assertThat(assetIds).doesNotContain(asset2.id.toString())
        }
    }

    @Nested
    @DisplayName("Asset Mutation Tests")
    inner class AssetMutationTests {
        @Test
        fun `should create asset upload`() {
            // Arrange
            val query =
                mapOf(
                    "query" to
                        """
                        mutation CreateAssetUpload(${'$'}input: CreateAssetUploadInput!) {
                            createAssetUpload(input: ${'$'}input) {
                                id
                                uploadUrl
                                uploadExpiresAt
                                status
                                namespace
                                resourceType
                                resourceId
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "input" to
                                mapOf(
                                    "namespace" to "user-profiles",
                                    "resourceType" to "PROFILE_IMAGE",
                                    "resourceId" to "user-123",
                                    "filename" to "avatar.jpg",
                                    "mimeType" to "image/jpeg",
                                    "sizeBytes" to 1048576L,
                                ),
                        ),
                )

            // Act
            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token")

            val response = httpClient.exchangeAsString(request)

            // Assert
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            val asset = data["createAssetUpload"]
            Assertions.assertThat(asset).isNotNull()
            Assertions.assertThat(asset["id"].stringValue).isNotEmpty()
            Assertions.assertThat(asset["uploadUrl"].stringValue).isNotEmpty()
            Assertions.assertThat(asset["status"].stringValue).isEqualTo("PENDING")
            Assertions.assertThat(asset["namespace"].stringValue).isEqualTo("user-profiles")
            Assertions.assertThat(asset["resourceType"].stringValue).isEqualTo("PROFILE_IMAGE")
        }

        @Test
        fun `should confirm asset upload`() {
            // Arrange
            val asset = createTestAsset(status = AssetStatus.PENDING)
            mockStorageClient.simulateUpload(asset.storageKey, sizeBytes = 1024L, contentType = "image/jpeg")

            val query =
                mapOf(
                    "query" to
                        """
                        mutation ConfirmAssetUpload(${'$'}input: ConfirmAssetUploadInput!) {
                            confirmAssetUpload(input: ${'$'}input) {
                                id
                                status
                                publicUrl
                                sizeBytes
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "input" to
                                mapOf(
                                    "assetId" to asset.id.toString(),
                                ),
                        ),
                )

            // Act
            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token")

            val response = httpClient.exchangeAsString(request)

            // Assert
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            val confirmedAsset = data["confirmAssetUpload"]
            Assertions.assertThat(confirmedAsset).isNotNull()
            Assertions.assertThat(confirmedAsset["id"].stringValue).isEqualTo(asset.id.toString())
            Assertions.assertThat(confirmedAsset["status"].stringValue).isEqualTo("READY")
            Assertions.assertThat(confirmedAsset["publicUrl"].stringValue).isNotEmpty()
        }

        @Test
        fun `should delete asset`() {
            // Arrange
            val asset = createTestAsset()
            mockStorageClient.simulateUpload(asset.storageKey, sizeBytes = 1024L)

            val query =
                mapOf(
                    "query" to
                        """
                        mutation DeleteAsset(${'$'}assetId: ID!) {
                            deleteAsset(assetId: ${'$'}assetId)
                        }
                        """.trimIndent(),
                    "variables" to mapOf("assetId" to asset.id.toString()),
                )

            // Act
            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token")

            val response = httpClient.exchangeAsString(request)

            // Assert
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            Assertions.assertThat(data["deleteAsset"].booleanValue).isTrue()

            // Verify asset is soft-deleted
            val deletedAsset = assetRepository.findById(asset.id!!).orElse(null)
            Assertions.assertThat(deletedAsset).isNotNull()
            Assertions.assertThat(deletedAsset?.status).isEqualTo(AssetStatus.DELETED)
            Assertions.assertThat(deletedAsset?.deletedAt).isNotNull()
        }
    }
}

