package io.github.salomax.neotool.banking.infra.assets

import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import java.util.UUID

data class PdfImportAssetUploadResult(
    val assetId: UUID,
    val downloadUrl: String,
)

@Singleton
open class PdfImportAssetStorage(
    private val assetsClient: AssetsServiceClient,
    private val presignedUrlHttpClient: PresignedUrlHttpClient,
    @Property(name = "banking.assets-service.pdf-import-namespace", defaultValue = "pdf-imports")
    private val pdfImportNamespace: String,
    @Property(name = "banking.assets-service.pdf-download-ttl-seconds", defaultValue = "7200")
    private val downloadTtlSeconds: Int,
) {
    fun uploadPdf(accountId: UUID, fileName: String, bytes: ByteArray): PdfImportAssetUploadResult {
        val upload = assetsClient.createAssetUpload(
            namespace = pdfImportNamespace,
            ownerId = accountId.toString(),
            mimeType = "application/pdf",
            originalFilename = fileName,
            sizeBytes = bytes.size.toLong(),
        )
        presignedUrlHttpClient.put(upload.uploadUrl, bytes, "application/pdf")
        assetsClient.confirmAssetUpload(upload.id)
        val asset = assetsClient.getAsset(upload.id, downloadTtlSeconds = downloadTtlSeconds)
        val downloadUrl = asset.downloadUrl
            ?: throw IllegalStateException("Asset ${upload.id} has no download URL after confirm")
        return PdfImportAssetUploadResult(
            assetId = upload.id,
            downloadUrl = downloadUrl,
        )
    }
}

