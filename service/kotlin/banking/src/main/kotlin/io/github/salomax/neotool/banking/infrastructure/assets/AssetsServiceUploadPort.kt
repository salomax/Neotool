package io.github.salomax.neotool.banking.infrastructure.assets

import io.github.salomax.neotool.banking.domain.issuer.port.AssetUploadPort
import io.github.salomax.neotool.banking.infra.assets.AssetsServiceException
import io.github.salomax.neotool.common.service.GraphQLServiceClient
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * [AssetUploadPort] implementation that delegates to the assets service via GraphQL (Apollo Router).
 *
 * Upload flow:
 * 1. `createAssetUpload` mutation → assets service creates a PENDING record and returns a presigned PUT URL.
 * 2. HTTP PUT the raw bytes directly to the presigned URL (S3/R2).
 * 3. `confirmAssetUpload` mutation → assets service marks the asset as READY.
 *
 * Returns the asset `id` (UUID) so callers can store it as a federation reference:
 *   `{ __typename: "Asset", id: <returned-id> }`
 *
 * Requires [graphql.router.url] to be configured (auto-disabled when missing).
 */
@Singleton
@Requires(beans = [GraphQLServiceClient::class])
open class AssetsServiceUploadPort(
    private val graphQLServiceClient: GraphQLServiceClient,
) : AssetUploadPort {
    private val logger = KotlinLogging.logger {}

    private val httpClient: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    override fun upload(
        key: String,
        bytes: ByteArray,
        contentType: String,
    ): String {
        // ── Step 1: createAssetUpload ────────────────────────────────────────
        val createMutation =
            """
            mutation CreateAssetUpload(${'$'}input: CreateAssetUploadInput!) {
              createAssetUpload(input: ${'$'}input) {
                id
                uploadUrl
              }
            }
            """.trimIndent()

        val createVariables =
            mapOf(
                "input" to
                    mapOf(
                        "namespace" to NAMESPACE,
                        "filename" to key,
                        "mimeType" to contentType,
                        "sizeBytes" to bytes.size.toLong(),
                    ),
            )

        logger.debug { "AssetsUpload: creating asset for '$key' (${bytes.size} bytes, $contentType)" }

        val createResponse =
            runBlocking {
                graphQLServiceClient.mutation(
                    mutation = createMutation,
                    variables = createVariables,
                    targetAudience = "apollo-router",
                )
            }

        if (!createResponse.errors.isNullOrEmpty()) {
            throw AssetsServiceException.invalidResponse(
                operation = "createAssetUpload",
                field = "createAssetUpload",
                details = createResponse.errors.toString(),
            )
        }

        val assetData =
            createResponse.data?.get("createAssetUpload") as? Map<*, *>
                ?: throw AssetsServiceException.invalidResponse(
                    operation = "createAssetUpload",
                    field = "createAssetUpload",
                )

        val assetId =
            assetData["id"] as? String
                ?: throw AssetsServiceException.invalidResponse(
                    operation = "createAssetUpload",
                    field = "createAssetUpload.id",
                )

        val uploadUrl =
            assetData["uploadUrl"] as? String
                ?: throw AssetsServiceException.invalidResponse(
                    operation = "createAssetUpload",
                    field = "createAssetUpload.uploadUrl",
                )

        // ── Step 2: PUT bytes to presigned URL ───────────────────────────────
        logger.debug { "AssetsUpload: uploading ${bytes.size} bytes to presigned URL for '$key'" }

        val putRequest =
            HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build()

        val putResponse = httpClient.send(putRequest, HttpResponse.BodyHandlers.discarding())

        if (putResponse.statusCode() !in 200..299) {
            throw AssetsServiceException.transferFailed(
                operation = "PUT",
                statusCode = putResponse.statusCode(),
            )
        }

        // ── Step 3: confirmAssetUpload ───────────────────────────────────────
        val confirmMutation =
            """
            mutation ConfirmAssetUpload(${'$'}input: ConfirmAssetUploadInput!) {
              confirmAssetUpload(input: ${'$'}input) {
                id
                status
              }
            }
            """.trimIndent()

        val confirmVariables =
            mapOf(
                "input" to
                    mapOf(
                        "assetId" to assetId,
                    ),
            )

        val confirmResponse =
            runBlocking {
                graphQLServiceClient.mutation(
                    mutation = confirmMutation,
                    variables = confirmVariables,
                    targetAudience = "apollo-router",
                )
            }

        if (!confirmResponse.errors.isNullOrEmpty()) {
            throw AssetsServiceException.invalidResponse(
                operation = "confirmAssetUpload",
                field = "confirmAssetUpload",
                details = confirmResponse.errors.toString(),
            )
        }

        val confirmedAsset =
            confirmResponse.data?.get("confirmAssetUpload") as? Map<*, *>
                ?: throw AssetsServiceException.invalidResponse(
                    operation = "confirmAssetUpload",
                    field = "confirmAssetUpload",
                )

        val confirmedId =
            confirmedAsset["id"] as? String
                ?: throw AssetsServiceException.invalidResponse(
                    operation = "confirmAssetUpload",
                    field = "confirmAssetUpload.id",
                )

        logger.debug { "AssetsUpload: asset '$confirmedId' READY for '$key'" }
        return confirmedId
    }

    private companion object {
        const val NAMESPACE = "institution-logos"
    }
}
