package io.github.salomax.neotool.banking.infra.assets

import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.ConflictException
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
    @Property(name = "banking.assets-service.pdf-import-namespace", defaultValue = "transaction-imports")
    private val pdfImportNamespace: String,
    @Property(name = "banking.assets-service.pdf-download-ttl-seconds", defaultValue = "7200")
    private val downloadTtlSeconds: Int,
) {
    fun uploadPdf(
        accountId: UUID,
        fileName: String,
        bytes: ByteArray,
    ): PdfImportAssetUploadResult {
        val upload =
            assetsClient.createAssetUpload(
                namespace = pdfImportNamespace,
                mimeType = "application/pdf",
                originalFilename = fileName,
                sizeBytes = bytes.size.toLong(),
                delegatedBy = accountId.toString(),
            )
        presignedUrlHttpClient.put(upload.uploadUrl, bytes, "application/pdf")
        assetsClient.confirmAssetUpload(upload.id, delegatedBy = accountId.toString())
        val asset =
            assetsClient.getAsset(
                upload.id,
                downloadTtlSeconds = downloadTtlSeconds,
                delegatedBy = accountId.toString(),
            )
        val downloadUrl =
            asset.downloadUrl
                ?: throw ConflictException(
                    errorCode = BankingErrorCode.IMPORT_ASSET_DOWNLOAD_URL_MISSING,
                    field = "assetId",
                    parameters = mapOf("assetId" to upload.id.toString()),
                )
        return PdfImportAssetUploadResult(
            assetId = upload.id,
            downloadUrl = downloadUrl,
        )
    }
}
