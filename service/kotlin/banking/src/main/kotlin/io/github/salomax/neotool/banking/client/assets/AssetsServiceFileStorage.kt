package io.github.salomax.neotool.banking.client.assets

import io.github.salomax.neotool.banking.domain.import.port.ImportFileStoragePort
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.ConflictException
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

/**
 * [ImportFileStoragePort] implementation that delegates to the assets service.
 * Uses presigned URLs for PUT (upload) and GET (download); delete is best-effort and idempotent.
 */
@Singleton
open class AssetsServiceFileStorage(
    private val assetsClient: AssetsServiceClient,
    private val presignedUrlHttpClient: PresignedUrlHttpClient,
    @Property(name = "banking.assets-service.import-namespace", defaultValue = "transaction-imports")
    private val importNamespace: String,
) : ImportFileStoragePort {
    private val log = KotlinLogging.logger {}

    override fun store(
        accountId: UUID,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): UUID {
        val upload =
            assetsClient.createAssetUpload(
                namespace = importNamespace,
                mimeType = mimeType,
                originalFilename = fileName,
                sizeBytes = bytes.size.toLong(),
                delegatedBy = accountId.toString(),
            )
        presignedUrlHttpClient.put(upload.uploadUrl, bytes, mimeType)
        assetsClient.confirmAssetUpload(upload.id, delegatedBy = accountId.toString())
        return upload.id
    }

    override fun read(
        accountId: UUID,
        assetId: UUID,
    ): ByteArray = presignedUrlHttpClient.get(resolveDownloadUrl(accountId, assetId, ttlSeconds = 3600))

    override fun resolveDownloadUrl(
        accountId: UUID,
        assetId: UUID,
        ttlSeconds: Int,
    ): String {
        val asset = assetsClient.getAsset(assetId, downloadTtlSeconds = ttlSeconds, delegatedBy = accountId.toString())
        return asset.downloadUrl
            ?: throw ConflictException(
                errorCode = BankingErrorCode.IMPORT_ASSET_DOWNLOAD_URL_MISSING,
                field = "assetId",
                parameters = mapOf("assetId" to assetId.toString()),
            )
    }

    override fun delete(
        accountId: UUID,
        assetId: UUID,
    ) {
        runCatching { assetsClient.deleteAsset(assetId, delegatedBy = accountId.toString()) }
            .onFailure { log.warn(it) { "Failed to delete import asset $assetId" } }
    }
}
