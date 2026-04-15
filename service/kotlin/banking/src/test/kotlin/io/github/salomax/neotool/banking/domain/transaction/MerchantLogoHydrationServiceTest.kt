package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.client.assets.AssetsServiceClient
import io.github.salomax.neotool.banking.client.assets.CreateAssetUploadResult
import io.github.salomax.neotool.banking.client.assets.PresignedUrlHttpClient
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.service.transaction.MerchantLogoHydrationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class MerchantLogoHydrationServiceTest {
    private lateinit var merchantRepository: MerchantRepository
    private lateinit var assetsServiceClient: AssetsServiceClient
    private lateinit var presignedUrlHttpClient: PresignedUrlHttpClient

    private lateinit var service: MerchantLogoHydrationService

    @BeforeEach
    fun setUp() {
        merchantRepository = mock()
        assetsServiceClient = mock()
        presignedUrlHttpClient = mock()
        service =
            MerchantLogoHydrationService(
                merchantRepository = merchantRepository,
                assetsServiceClient = assetsServiceClient,
                presignedUrlHttpClient = presignedUrlHttpClient,
                logoDevToken = "token-123",
                merchantLogoNamespace = "merchant-logos",
            )
    }

    @Test
    fun `hydrateForDomain skips when cache is fresh`() {
        whenever(merchantRepository.findLogoCacheByDomain("netflix.com")).thenReturn(
            MerchantLogoCacheEntry(
                domain = "netflix.com",
                assetId = "asset-1",
                fetchedAt = Instant.now().minus(5, ChronoUnit.DAYS),
            ),
        )

        service.hydrateForDomain("netflix.com")

        verify(assetsServiceClient, never()).createAssetUpload(any(), any(), any(), any(), any())
        verify(presignedUrlHttpClient, never()).get(any())
    }

    @Test
    fun `hydrateForDomain uploads logo and updates cache when stale`() {
        val staleFetchedAt = Instant.now().minus(40, ChronoUnit.DAYS)
        whenever(merchantRepository.findLogoCacheByDomain("netflix.com")).thenReturn(
            MerchantLogoCacheEntry(
                domain = "netflix.com",
                assetId = "old-asset",
                fetchedAt = staleFetchedAt,
            ),
        )
        whenever(presignedUrlHttpClient.get(any())).thenReturn(byteArrayOf(1, 2, 3))
        whenever(
            assetsServiceClient.createAssetUpload(
                namespace = eq("merchant-logos"),
                mimeType = eq("image/png"),
                originalFilename = eq("netflix.com.png"),
                sizeBytes = eq(3L),
                delegatedBy = eq(null),
            ),
        ).thenReturn(
            CreateAssetUploadResult(
                id = UUID.fromString("00000000-0000-0000-0000-000000000010"),
                uploadUrl = "https://upload.example.com",
            ),
        )

        service.hydrateForDomain("https://www.netflix.com/plans")

        verify(presignedUrlHttpClient).put("https://upload.example.com", byteArrayOf(1, 2, 3), "image/png")
        verify(assetsServiceClient).confirmAssetUpload(UUID.fromString("00000000-0000-0000-0000-000000000010"), null)
        verify(merchantRepository).upsertLogoCache(eq("netflix.com"), eq("00000000-0000-0000-0000-000000000010"), any())
    }
}
