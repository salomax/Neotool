package io.github.salomax.neotool.banking.infra.assets

import io.github.salomax.neotool.banking.domain.import.port.ImportFileStoragePort
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
                    ownerId = eq(accountId.toString()),
                    mimeType = eq(mimeType),
                    originalFilename = eq(fileName),
                    sizeBytes = eq(bytes.size.toLong()),
                ),
            ).thenReturn(CreateAssetUploadResult(id = assetId, uploadUrl = uploadUrl))
            whenever(assetsClient.confirmAssetUpload(eq(assetId))).thenReturn(
                ConfirmAssetUploadResult(id = assetId, status = "READY"),
            )

            val result = storage.store(accountId, bytes, fileName, mimeType)

            assertThat(result).isEqualTo(assetId)
            verify(presignedUrlHttpClient).put(uploadUrl, bytes, mimeType)
            verify(assetsClient).confirmAssetUpload(assetId)
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
            whenever(assetsClient.getAsset(eq(assetId), eq(3600)))
                .thenReturn(AssetResult(id = assetId, downloadUrl = downloadUrl))
            whenever(presignedUrlHttpClient.get(eq(downloadUrl))).thenReturn(bytes)

            val result = storage.read(assetId)

            assertThat(result).isEqualTo(bytes)
        }

        @Test
        fun `throws when asset has no downloadUrl`() {
            whenever(assetsClient.getAsset(eq(assetId), any()))
                .thenReturn(AssetResult(id = assetId, downloadUrl = null))

            assertThat(
                assertThrows<IllegalStateException> { storage.read(assetId) }.message,
            ).contains("no download URL")
        }
    }

    @Nested
    @DisplayName("delete")
    inner class DeleteTests {

        @Test
        fun `calls deleteAsset and does not throw`() {
            whenever(assetsClient.deleteAsset(eq(assetId))).thenReturn(true)

            storage.delete(assetId)

            verify(assetsClient).deleteAsset(assetId)
        }

        @Test
        fun `does not throw when deleteAsset fails`() {
            whenever(assetsClient.deleteAsset(any())).thenThrow(AssetsServiceException("Not found"))

            storage.delete(assetId)
        }
    }
}
