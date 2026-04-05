package io.github.salomax.neotool.banking.client.assets

import io.github.salomax.neotool.banking.client.assets.*
import io.github.salomax.neotool.banking.client.assets.ImportFileStoragePort
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.ConflictException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("AssetsServiceFileStorage")
class AssetsServiceFileStorageTest {
    private lateinit var assetsClient: AssetsServiceClient
    private lateinit var presignedUrlHttpClient: PresignedUrlHttpClient
    private lateinit var storage: ImportFileStoragePort

    private val accountId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()
    private val uploadUrl = "https://s3.example.com/presigned-put"
    private val downloadUrl = "https://s3.example.com/presigned-get"
    private val bytes = "OFX content".toByteArray()
    private val fileName = "export.ofx"
    private val mimeType = "application/x-ofx"
    private val importNamespace = "transaction-imports"

    @BeforeEach
    fun setUp() {
        assetsClient = mock()
        presignedUrlHttpClient = mock()
        storage = AssetsServiceFileStorage(assetsClient, presignedUrlHttpClient, importNamespace)
    }

    @Nested
    @DisplayName("store")
    inner class StoreTests {
        @Test
        fun `calls createAssetUpload PUT and confirmAssetUpload returns assetId`() {
            whenever(
                assetsClient.createAssetUpload(
                    namespace = eq(importNamespace),
                    mimeType = eq(mimeType),
                    originalFilename = eq(fileName),
                    sizeBytes = eq(bytes.size.toLong()),
                    delegatedBy = eq(accountId.toString()),
                ),
            ).thenReturn(CreateAssetUploadResult(id = assetId, uploadUrl = uploadUrl))
            whenever(assetsClient.confirmAssetUpload(eq(assetId), eq(accountId.toString()))).thenReturn(
                ConfirmAssetUploadResult(id = assetId, status = "READY"),
            )

            val result = storage.store(accountId, bytes, fileName, mimeType)

            assertThat(result).isEqualTo(assetId)
            verify(presignedUrlHttpClient).put(uploadUrl, bytes, mimeType)
            verify(assetsClient).confirmAssetUpload(assetId, accountId.toString())
        }

        @Test
        fun `propagates exception when createAssetUpload fails`() {
            whenever(assetsClient.createAssetUpload(any(), any(), any(), any(), any()))
                .thenThrow(AssetsServiceException("Assets service down"))

            assertThrows<AssetsServiceException> {
                storage.store(accountId, bytes, fileName, mimeType)
            }
        }

        @Test
        fun `propagates exception when PUT fails`() {
            whenever(assetsClient.createAssetUpload(any(), any(), any(), any(), any()))
                .thenReturn(CreateAssetUploadResult(assetId, uploadUrl))
            whenever(presignedUrlHttpClient.put(any(), any(), any()))
                .thenThrow(AssetsServiceException("PUT failed"))

            assertThrows<AssetsServiceException> {
                storage.store(accountId, bytes, fileName, mimeType)
            }
        }
    }

    @Nested
    @DisplayName("read")
    inner class ReadTests {
        @Test
        fun `fetches asset gets downloadUrl and returns bytes from GET`() {
            whenever(assetsClient.getAsset(eq(assetId), eq(3600), eq(accountId.toString())))
                .thenReturn(AssetResult(id = assetId, mimeType = null, sizeBytes = null, originalFilename = null, downloadUrl = downloadUrl))
            whenever(presignedUrlHttpClient.get(eq(downloadUrl))).thenReturn(bytes)

            val result = storage.read(accountId, assetId)

            assertThat(result).isEqualTo(bytes)
        }

        @Test
        fun `throws when asset has no downloadUrl`() {
            whenever(assetsClient.getAsset(eq(assetId), any(), any()))
                .thenReturn(AssetResult(id = assetId, mimeType = null, sizeBytes = null, originalFilename = null, downloadUrl = null))

            val exception = assertThrows<ConflictException> { storage.read(accountId, assetId) }
            assertThat(exception.errorCode).isEqualTo(BankingErrorCode.IMPORT_ASSET_DOWNLOAD_URL_MISSING)
        }
    }

    @Nested
    @DisplayName("resolveDownloadUrl")
    inner class ResolveDownloadUrlTests {
        @Test
        fun `returns presigned url from assets service`() {
            whenever(assetsClient.getAsset(eq(assetId), eq(1800), eq(accountId.toString())))
                .thenReturn(AssetResult(id = assetId, mimeType = null, sizeBytes = null, originalFilename = null, downloadUrl = downloadUrl))

            val result = storage.resolveDownloadUrl(accountId, assetId, ttlSeconds = 1800)

            assertThat(result).isEqualTo(downloadUrl)
        }

        @Test
        fun `throws when resolved asset has no downloadUrl`() {
            whenever(assetsClient.getAsset(eq(assetId), any(), any()))
                .thenReturn(AssetResult(id = assetId, mimeType = null, sizeBytes = null, originalFilename = null, downloadUrl = null))

            val exception =
                assertThrows<ConflictException> {
                    storage.resolveDownloadUrl(accountId, assetId, ttlSeconds = 1800)
                }
            assertThat(exception.errorCode).isEqualTo(BankingErrorCode.IMPORT_ASSET_DOWNLOAD_URL_MISSING)
        }
    }

    @Nested
    @DisplayName("delete")
    inner class DeleteTests {
        @Test
        fun `calls deleteAsset and does not throw`() {
            whenever(assetsClient.deleteAsset(eq(assetId), eq(accountId.toString()))).thenReturn(true)

            storage.delete(accountId, assetId)

            verify(assetsClient).deleteAsset(assetId, accountId.toString())
        }

        @Test
        fun `does not throw when deleteAsset fails`() {
            whenever(assetsClient.deleteAsset(any(), any())).thenThrow(AssetsServiceException("Not found"))

            storage.delete(accountId, assetId)
        }
    }
}
