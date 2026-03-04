package io.github.salomax.neotool.banking.infra.assets

import io.github.salomax.neotool.common.security.service.GraphQLServiceClient
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * GraphQL client for the assets service.
 * Delegates to [GraphQLServiceClient] for outbound calls with service-token auth.
 */
@Singleton
open class AssetsServiceClient(
    @Property(name = "banking.assets-service.target-audience", defaultValue = "apollo-router")
    private val targetAudience: String,
    @Property(name = "security.service.id", defaultValue = "")
    private val serviceId: String?,
    @Property(name = "security.service.secret", defaultValue = "")
    private val serviceSecret: String?,
    @Property(name = "graphql.router.url", defaultValue = "http://localhost:4000/graphql")
    private val routerGraphqlUrl: String,
    private val graphQLServiceClient: GraphQLServiceClient,
    private val httpClient: HttpClient,
) {

    /**
     * Creates an asset upload and returns id + uploadUrl.
     * @param ownerId tenant ID (accountId) for imports namespace
     */
    fun createAssetUpload(
        namespace: String,
        ownerId: String,
        mimeType: String,
        originalFilename: String,
        sizeBytes: Long,
    ): CreateAssetUploadResult {
        val mutation = """
            mutation CreateAssetUpload(${'$'}input: CreateAssetUploadInput!) {
              createAssetUpload(input: ${'$'}input) {
                id
                uploadUrl
              }
            }
        """.trimIndent()
        val variables = mapOf(
            "input" to mapOf(
                "namespace" to namespace,
                "filename" to originalFilename,
                "mimeType" to mimeType,
                "sizeBytes" to sizeBytes,
                "ownerId" to ownerId,
            ),
        )
        val response = executeMutation(mutation, variables)
        val data = response.data?.get("createAssetUpload") as? Map<*, *>
            ?: throw AssetsServiceException("createAssetUpload returned no data: ${response.errors}")
        val id = data["id"] as? String ?: throw AssetsServiceException("createAssetUpload returned no id")
        val uploadUrl = data["uploadUrl"] as? String ?: throw AssetsServiceException("createAssetUpload returned no uploadUrl")
        return CreateAssetUploadResult(id = UUID.fromString(id), uploadUrl = uploadUrl)
    }

    fun confirmAssetUpload(assetId: UUID): ConfirmAssetUploadResult {
        val mutation = """
            mutation ConfirmAssetUpload(${'$'}input: ConfirmAssetUploadInput!) {
              confirmAssetUpload(input: ${'$'}input) {
                id
                status
              }
            }
        """.trimIndent()
        val variables = mapOf(
            "input" to mapOf("assetId" to assetId.toString()),
        )
        val response = executeMutation(mutation, variables)
        val data = response.data?.get("confirmAssetUpload") as? Map<*, *>
            ?: throw AssetsServiceException("confirmAssetUpload returned no data: ${response.errors}")
        val id = data["id"] as? String ?: throw AssetsServiceException("confirmAssetUpload returned no id")
        val status = data["status"] as? String ?: throw AssetsServiceException("confirmAssetUpload returned no status")
        return ConfirmAssetUploadResult(id = UUID.fromString(id), status = status)
    }

    /**
     * Fetches asset and returns downloadUrl with given TTL.
     */
    fun getAsset(assetId: UUID, downloadTtlSeconds: Int = 3600): AssetResult {
        val query = """
            query GetAsset(${'$'}id: ID!) {
              asset(id: ${'$'}id) {
                id
                downloadUrl(ttlSeconds: $downloadTtlSeconds)
              }
            }
        """.trimIndent()
        val variables = mapOf("id" to assetId.toString())
        val response = executeQuery(query, variables)
        val data = response.data?.get("asset") as? Map<*, *>
            ?: throw AssetsServiceException("asset not found: $assetId (${response.errors})")
        val id = data["id"] as? String ?: throw AssetsServiceException("asset returned no id")
        val downloadUrl = data["downloadUrl"] as? String
        return AssetResult(id = UUID.fromString(id), downloadUrl = downloadUrl)
    }

    fun deleteAsset(assetId: UUID): Boolean {
        val mutation = """
            mutation DeleteAsset(${'$'}assetId: ID!) {
              deleteAsset(assetId: ${'$'}assetId)
            }
        """.trimIndent()
        val variables = mapOf("assetId" to assetId.toString())
        val response = executeMutation(mutation, variables)
        return response.data?.get("deleteAsset") as? Boolean ?: false
    }

    private fun executeQuery(query: String, variables: Map<String, Any>): io.github.salomax.neotool.common.security.service.GraphQLResponse {
        return try {
            if (hasServiceCredentials()) {
                runBlocking { graphQLServiceClient.query(query, variables, targetAudience) }
            } else {
                postWithoutAuth(query, variables)
            }
        } catch (e: Exception) {
            throw AssetsServiceException("Assets service query failed: ${e.message}", e)
        }
    }

    private fun executeMutation(mutation: String, variables: Map<String, Any>): io.github.salomax.neotool.common.security.service.GraphQLResponse {
        return try {
            if (hasServiceCredentials()) {
                runBlocking { graphQLServiceClient.mutation(mutation, variables, targetAudience) }
            } else {
                postWithoutAuth(mutation, variables)
            }
        } catch (e: Exception) {
            throw AssetsServiceException("Assets service mutation failed: ${e.message}", e)
        }
    }

    private fun hasServiceCredentials(): Boolean =
        !serviceId.isNullOrBlank() && !serviceSecret.isNullOrBlank()

    private fun postWithoutAuth(
        query: String,
        variables: Map<String, Any>,
    ): io.github.salomax.neotool.common.security.service.GraphQLResponse {
        val requestBody = mapOf("query" to query, "variables" to variables)
        val request = HttpRequest.POST(routerGraphqlUrl, requestBody).contentType(MediaType.APPLICATION_JSON)
        val response = httpClient.toBlocking().exchange(request, Map::class.java)
        val body = response.body() as? Map<*, *> ?: emptyMap<Any, Any>()
        val data = (body["data"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value as Any }
        val errors = body["errors"] as? List<*>
        return io.github.salomax.neotool.common.security.service.GraphQLResponse(
            data = data,
            errors = errors?.mapNotNull { it as? Map<*, *> }?.map { it.mapKeys { k -> k.key.toString() }.mapValues { it.value as Any } },
        )
    }
}

data class CreateAssetUploadResult(val id: UUID, val uploadUrl: String)
data class ConfirmAssetUploadResult(val id: UUID, val status: String)
data class AssetResult(val id: UUID, val downloadUrl: String?)

class AssetsServiceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
