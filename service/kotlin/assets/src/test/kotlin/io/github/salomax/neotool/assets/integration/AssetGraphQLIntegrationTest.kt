package io.github.salomax.neotool.assets.test.integration

import io.github.salomax.neotool.assets.TestTokenPrincipalDecoder
import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.domain.AssetVisibility
import io.github.salomax.neotool.assets.entity.AssetEntity
import io.github.salomax.neotool.assets.repository.AssetRepository
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
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
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
    startApplication = true,
)
@DisplayName("Asset GraphQL Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("assets")
open class AssetGraphQLIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest,
    TestPropertyProvider {
    @Inject
    lateinit var assetRepository: AssetRepository

    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    @Named("mockStorageClient")
    lateinit var mockStorageClient: MockStorageClient

    // Use the same test user ID as TestTokenPrincipalDecoder to ensure authorization works
    private val testUserId = TestTokenPrincipalDecoder.TEST_USER_ID.toString()

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
        // Use PUBLIC so publicUrl is generated
        visibility: AssetVisibility = AssetVisibility.PUBLIC,
    ): AssetEntity {
        val assetId = UUID.randomUUID()
        val entity =
            AssetEntity(
                id = null,
                ownerId = ownerId,
                namespace = namespace,
                visibility = visibility,
                storageKey = "$namespace/$ownerId/$assetId",
                storageRegion = "us-east-1",
                storageBucket = if (visibility == AssetVisibility.PUBLIC) "neotool-assets-public" else "neotool-assets-private",
                mimeType = "image/jpeg",
                sizeBytes = 1024L,
                checksum = "test-checksum",
                originalFilename = "test.jpg",
                uploadUrl = if (status == AssetStatus.PENDING) "https://upload.example.com/presigned" else null,
                uploadExpiresAt = if (status == AssetStatus.PENDING) Instant.now().plusSeconds(900) else null,
                // No longer stored - generated dynamically
                publicUrl = null,
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
            val response =
                try {
                    httpClient.exchangeAsString(request)
                } catch (e: io.micronaut.http.client.exceptions.HttpClientResponseException) {
                    // If exchange throws, try to read the error response
                    val errorResponse = e.response
                    val errorBody =
                        if (errorResponse != null) {
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
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)

            // Check for errors first to get better error messages
            if (payload["errors"] != null && !payload["errors"].isNull && payload["errors"].size() > 0) {
                val errorMsg =
                    (0 until payload["errors"].size()).joinToString("\n") { i ->
                        val error = payload["errors"][i]
                        val message = error["message"]?.stringValue ?: "No message"
                        val path = error["path"]?.toString() ?: "No path"
                        "Error $i: $message (path: $path)"
                    }
                throw AssertionError("GraphQL errors:\n$errorMsg\nResponse: $response")
            }

            payload["errors"].assertNoErrors()

            val data = payload["data"]
            Assertions.assertThat(data).isNotNull()
            Assertions.assertThat(data.isNull).isFalse()

            val assetData = data["asset"]
            if (assetData == null || assetData.isNull) {
                // If asset is null, check if there are any errors we missed
                val errors = payload["errors"]
                val errorMsg =
                    if (errors != null && !errors.isNull && errors.size() > 0) {
                        (0 until errors.size()).joinToString("\n") { i ->
                            val error = errors[i]
                            val message = error["message"]?.stringValue ?: "No message"
                            val path = error["path"]?.toString() ?: "No path"
                            "Error $i: $message (path: $path)"
                        }
                    } else {
                        "No errors found, but asset is null. Response: $response"
                    }
                throw AssertionError("Asset data is null in GraphQL response:\n$errorMsg")
            }

            // Verify all fields with null-safe checks
            val idNode = assetData["id"]
            Assertions.assertThat(idNode).isNotNull()
            if (idNode.isNull) {
                throw AssertionError("Asset id is null. Asset data: $assetData")
            }
            Assertions.assertThat(idNode.stringValue).isEqualTo(asset.id.toString())

            Assertions.assertThat(assetData["ownerId"]?.stringValue).isEqualTo(testUserId)
            Assertions.assertThat(assetData["namespace"]?.stringValue).isEqualTo("user-profiles")
            Assertions.assertThat(assetData["status"]?.stringValue).isEqualTo("READY")
            Assertions.assertThat(assetData["mimeType"]?.stringValue).isEqualTo("image/jpeg")
            Assertions.assertThat(assetData["sizeBytes"]?.longValue).isEqualTo(1024L)
            // publicUrl is now generated dynamically, just verify it's present and not empty
            val publicUrlNode = assetData["publicUrl"]
            Assertions.assertThat(publicUrlNode).isNotNull()
            Assertions.assertThat(publicUrlNode.isNull).isFalse()
            Assertions.assertThat(publicUrlNode.stringValue).isNotEmpty()
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
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "input" to
                                mapOf(
                                    "namespace" to "user-profiles",
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
        }

        @Test
        fun `should confirm asset upload`() {
            // Arrange
            val asset = createTestAsset(status = AssetStatus.PENDING)
            mockStorageClient.simulateUpload(asset.storageBucket, asset.storageKey, sizeBytes = 1024L, contentType = "image/jpeg")

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
            mockStorageClient.simulateUpload(asset.storageBucket, asset.storageKey, sizeBytes = 1024L)

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

            // Verify asset is hard-deleted (should not exist)
            val deletedAsset = assetRepository.findById(asset.id!!)
            Assertions.assertThat(deletedAsset).isEmpty
        }
    }
}
