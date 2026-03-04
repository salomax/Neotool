package io.github.salomax.neotool.banking.infra.assets

import io.github.salomax.neotool.banking.domain.import.port.ImportFileStoragePort
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

    override fun store(accountId: UUID, bytes: ByteArray, fileName: String, mimeType: String): UUID {
        val upload = assetsClient.createAssetUpload(
            namespace = importNamespace,
            ownerId = accountId.toString(),
            mimeType = mimeType,
            originalFilename = fileName,
            sizeBytes = bytes.size.toLong(),
        )
        presignedUrlHttpClient.put(upload.uploadUrl, bytes, mimeType)
        assetsClient.confirmAssetUpload(upload.id)
        return upload.id
    }

    override fun read(assetId: UUID): ByteArray {
        val asset = assetsClient.getAsset(assetId, downloadTtlSeconds = 3600)
        val downloadUrl = asset.downloadUrl
            ?: throw IllegalStateException("Asset $assetId has no download URL (may not be READY)")
        return presignedUrlHttpClient.get(downloadUrl)
    }

    override fun delete(assetId: UUID) {
        runCatching { assetsClient.deleteAsset(assetId) }
            .onFailure { log.warn(it) { "Failed to delete import asset $assetId" } }
    }
}
