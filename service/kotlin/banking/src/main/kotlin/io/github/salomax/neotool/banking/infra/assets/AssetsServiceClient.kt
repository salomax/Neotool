package io.github.salomax.neotool.banking.infra.assets

import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.service.GraphQLServiceClient
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
     * @param delegatedBy tenant context (accountId) propagated via delegated service token claim
     */
    fun createAssetUpload(
        namespace: String,
        mimeType: String,
        originalFilename: String,
        sizeBytes: Long,
        delegatedBy: String? = null,
    ): CreateAssetUploadResult {
        val mutation =
            """
            mutation CreateAssetUpload(${'$'}input: CreateAssetUploadInput!) {
              createAssetUpload(input: ${'$'}input) {
                id
                uploadUrl
              }
            }
            """.trimIndent()
        val variables =
            mapOf(
                "input" to
                    mapOf(
                        "namespace" to namespace,
                        "filename" to originalFilename,
                        "mimeType" to mimeType,
                        "sizeBytes" to sizeBytes,
                    ),
            )
        val response = executeMutation(mutation, variables, delegatedBy)
        val data =
            response.data?.get("createAssetUpload") as? Map<*, *>
                ?: throw AssetsServiceException.invalidResponse(
                    operation = "createAssetUpload",
                    field = "createAssetUpload",
                    details = response.errors?.toString(),
                )
        val id =
            data["id"] as? String
                ?: throw AssetsServiceException.invalidResponse(
                    operation = "createAssetUpload",
                    field = "createAssetUpload.id",
                )
        val uploadUrl =
            data["uploadUrl"] as? String ?: throw AssetsServiceException.invalidResponse(
                operation = "createAssetUpload",
                field = "createAssetUpload.uploadUrl",
            )
        val parsedId =
            runCatching { UUID.fromString(id) }.getOrElse {
                throw AssetsServiceException.invalidResponse(
                    operation = "createAssetUpload",
                    field = "createAssetUpload.id",
                    details = "invalid UUID returned by assets service",
                )
            }
        return CreateAssetUploadResult(id = parsedId, uploadUrl = uploadUrl)
    }

    fun confirmAssetUpload(
        assetId: UUID,
        delegatedBy: String? = null,
    ): ConfirmAssetUploadResult {
        val mutation =
            """
            mutation ConfirmAssetUpload(${'$'}input: ConfirmAssetUploadInput!) {
              confirmAssetUpload(input: ${'$'}input) {
                id
                status
              }
            }
            """.trimIndent()
        val variables =
            mapOf(
                "input" to mapOf("assetId" to assetId.toString()),
            )
        val response = executeMutation(mutation, variables, delegatedBy)
        val data =
            response.data?.get("confirmAssetUpload") as? Map<*, *>
                ?: throw AssetsServiceException.invalidResponse(
                    operation = "confirmAssetUpload",
                    field = "confirmAssetUpload",
                    details = response.errors?.toString(),
                )
        val id =
            data["id"] as? String
                ?: throw AssetsServiceException.invalidResponse(
                    operation = "confirmAssetUpload",
                    field = "confirmAssetUpload.id",
                )
        val status =
            data["status"] as? String
                ?: throw AssetsServiceException.invalidResponse(
                    operation = "confirmAssetUpload",
                    field = "confirmAssetUpload.status",
                )
        val parsedId =
            runCatching { UUID.fromString(id) }.getOrElse {
                throw AssetsServiceException.invalidResponse(
                    operation = "confirmAssetUpload",
                    field = "confirmAssetUpload.id",
                    details = "invalid UUID returned by assets service",
                )
            }
        return ConfirmAssetUploadResult(id = parsedId, status = status)
    }

    /**
     * Fetches asset and returns downloadUrl with given TTL.
     */
    fun getAsset(
        assetId: UUID,
        downloadTtlSeconds: Int = 3600,
        delegatedBy: String? = null,
    ): AssetResult {
        val query =
            """
            query GetAsset(${'$'}id: ID!) {
              asset(id: ${'$'}id) {
                id
                downloadUrl(ttlSeconds: $downloadTtlSeconds)
              }
            }
            """.trimIndent()
        val variables = mapOf("id" to assetId.toString())
        val response = executeQuery(query, variables, delegatedBy)
        val data =
            response.data?.get("asset") as? Map<*, *>
                ?: throw AssetsServiceException.notFound(assetId)
        val id =
            data["id"] as? String
                ?: throw AssetsServiceException.invalidResponse(
                    operation = "asset",
                    field = "asset.id",
                )
        val downloadUrl = data["downloadUrl"] as? String
        val parsedId =
            runCatching { UUID.fromString(id) }.getOrElse {
                throw AssetsServiceException.invalidResponse(
                    operation = "asset",
                    field = "asset.id",
                    details = "invalid UUID returned by assets service",
                )
            }
        return AssetResult(id = parsedId, downloadUrl = downloadUrl)
    }

    fun deleteAsset(
        assetId: UUID,
        delegatedBy: String? = null,
    ): Boolean {
        val mutation =
            """
            mutation DeleteAsset(${'$'}assetId: ID!) {
              deleteAsset(assetId: ${'$'}assetId)
            }
            """.trimIndent()
        val variables = mapOf("assetId" to assetId.toString())
        val response = executeMutation(mutation, variables, delegatedBy)
        return response.data?.get("deleteAsset") as? Boolean ?: false
    }

    private fun executeQuery(
        query: String,
        variables: Map<String, Any>,
        delegatedBy: String? = null,
    ): io.github.salomax.neotool.common.service.GraphQLResponse {
        return try {
            if (hasServiceCredentials()) {
                runBlocking { graphQLServiceClient.query(query, variables, targetAudience, delegatedBy) }
            } else {
                postWithoutAuth(query, variables)
            }
        } catch (e: DomainException) {
            throw e
        } catch (e: Exception) {
            throw AssetsServiceException.serviceUnavailable(operation = "query", cause = e)
        }
    }

    private fun executeMutation(
        mutation: String,
        variables: Map<String, Any>,
        delegatedBy: String? = null,
    ): io.github.salomax.neotool.common.service.GraphQLResponse {
        return try {
            if (hasServiceCredentials()) {
                runBlocking { graphQLServiceClient.mutation(mutation, variables, targetAudience, delegatedBy) }
            } else {
                postWithoutAuth(mutation, variables)
            }
        } catch (e: DomainException) {
            throw e
        } catch (e: Exception) {
            throw AssetsServiceException.serviceUnavailable(operation = "mutation", cause = e)
        }
    }

    private fun hasServiceCredentials(): Boolean = !serviceId.isNullOrBlank() && !serviceSecret.isNullOrBlank()

    private fun postWithoutAuth(
        query: String,
        variables: Map<String, Any>,
    ): io.github.salomax.neotool.common.service.GraphQLResponse {
        val requestBody = mapOf("query" to query, "variables" to variables)
        val request = HttpRequest.POST(routerGraphqlUrl, requestBody).contentType(MediaType.APPLICATION_JSON)
        val response = httpClient.toBlocking().exchange(request, Map::class.java)
        val body = response.body() as? Map<*, *> ?: emptyMap<Any, Any>()
        val data = (body["data"] as? Map<*, *>)?.toStringKeyedMap()
        val errors = body["errors"] as? List<*>
        return io.github.salomax.neotool.common.service.GraphQLResponse(
            data = data,
            errors =
                errors?.mapNotNull { it as? Map<*, *> }?.map {
                    it.mapKeys {
                            k ->
                        k.key.toString()
                    }.mapValues { it.value }
                },
        )
    }

    private fun Map<*, *>.toStringKeyedMap(): Map<String, Any?> =
        entries.associate { (key, value) -> key.toString() to value }
}

data class CreateAssetUploadResult(val id: UUID, val uploadUrl: String)

data class ConfirmAssetUploadResult(val id: UUID, val status: String)

data class AssetResult(val id: UUID, val downloadUrl: String?)

class AssetsServiceException(
    errorCode: io.github.salomax.neotool.common.error.ErrorCode,
    field: String? = null,
    parameters: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(errorCode = errorCode, field = field, parameters = parameters, cause = cause) {
    constructor(
        message: String,
        cause: Throwable? = null,
    ) : this(
        errorCode = BankingErrorCode.IMPORT_ASSET_SERVICE_UNAVAILABLE,
        field = "details",
        parameters = mapOf("details" to message),
        cause = cause,
    )

    companion object {
        fun serviceUnavailable(
            operation: String,
            cause: Throwable? = null,
        ): AssetsServiceException =
            AssetsServiceException(
                errorCode = BankingErrorCode.IMPORT_ASSET_SERVICE_UNAVAILABLE,
                field = "operation",
                parameters = mapOf("operation" to operation),
                cause = cause,
            )

        fun invalidResponse(
            operation: String,
            field: String? = null,
            details: String? = null,
        ): AssetsServiceException =
            AssetsServiceException(
                errorCode = BankingErrorCode.IMPORT_ASSET_INVALID_RESPONSE,
                field = field,
                parameters =
                    buildMap {
                        put("operation", operation)
                        details?.let { put("details", it) }
                    },
            )

        fun notFound(assetId: UUID): AssetsServiceException =
            AssetsServiceException(
                errorCode = BankingErrorCode.IMPORT_ASSET_NOT_FOUND,
                field = "assetId",
                parameters = mapOf("assetId" to assetId.toString()),
            )

        fun transferFailed(
            operation: String,
            statusCode: Int,
        ): AssetsServiceException =
            AssetsServiceException(
                errorCode = BankingErrorCode.IMPORT_ASSET_TRANSFER_FAILED,
                field = "statusCode",
                parameters =
                    mapOf(
                        "operation" to operation,
                        "statusCode" to statusCode,
                    ),
            )
    }
}
