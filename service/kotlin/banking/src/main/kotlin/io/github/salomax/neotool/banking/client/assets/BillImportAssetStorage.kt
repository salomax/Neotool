package io.github.salomax.neotool.banking.client.assets

import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.ConflictException
import io.micronaut.context.annotation.Property
import io.micronaut.context.env.Environment
import jakarta.inject.Singleton
import java.util.UUID

data class BillImportAssetUploadResult(
    val assetId: UUID,
    val downloadUrl: String,
)

@Singleton
open class BillImportAssetStorage(
    private val assetsClient: AssetsServiceClient,
    private val presignedUrlHttpClient: PresignedUrlHttpClient,
    @param:Property(name = "banking.assets-service.bill-import-namespace", defaultValue = "transaction-imports")
    private val billImportNamespace: String,
    @param:Property(name = "banking.assets-service.bill-download-ttl-seconds", defaultValue = "7200")
    private val billDownloadTtlSeconds: Int,
    private val environment: Environment,
) {
    fun uploadPdf(
        accountId: UUID,
        fileName: String,
        bytes: ByteArray,
    ): BillImportAssetUploadResult {
        val namespace = resolveNamespace()
        val upload =
            assetsClient.createAssetUpload(
                namespace = namespace,
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
                downloadTtlSeconds = resolveDownloadTtlSeconds(),
                delegatedBy = accountId.toString(),
            )
        val downloadUrl =
            asset.downloadUrl
                ?: throw ConflictException(
                    errorCode = BankingErrorCode.IMPORT_ASSET_DOWNLOAD_URL_MISSING,
                    field = "assetId",
                    parameters = mapOf("assetId" to upload.id.toString()),
                )
        return BillImportAssetUploadResult(
            assetId = upload.id,
            downloadUrl = downloadUrl,
        )
    }

    internal fun resolveNamespace(): String =
        firstUsableValue(
            billImportNamespace,
            environment.getProperty("banking.assets-service.pdf-import-namespace", String::class.java).orElse(null),
            System.getenv("ASSETS_PDF_IMPORT_NAMESPACE"),
        ) ?: "transaction-imports"

    internal fun resolveDownloadTtlSeconds(): Int =
        firstPositiveValue(
            billDownloadTtlSeconds,
            environment.getProperty("banking.assets-service.pdf-download-ttl-seconds", Int::class.java).orElse(null),
            System.getenv("ASSETS_PDF_DOWNLOAD_TTL_SECONDS")?.toIntOrNull(),
        ) ?: 7200

    private fun firstUsableValue(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && !looksLikeUnresolvedPlaceholder(it) }?.trim()

    private fun firstPositiveValue(vararg values: Int?): Int? = values.firstOrNull { (it ?: 0) > 0 }

    private fun looksLikeUnresolvedPlaceholder(value: String): Boolean = value.contains("\${") || value.contains("}")
}
