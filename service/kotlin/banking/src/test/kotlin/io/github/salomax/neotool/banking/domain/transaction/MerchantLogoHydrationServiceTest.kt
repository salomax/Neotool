package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.client.assets.AssetsServiceClient
import io.github.salomax.neotool.banking.client.assets.CreateAssetUploadResult
import io.github.salomax.neotool.banking.client.assets.PresignedUrlHttpClient
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.service.transaction.MerchantLogoHydrationService
import io.github.salomax.neotool.banking.service.transaction.MerchantLogoUrlBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MerchantLogoHydrationServiceTest {
    private lateinit var merchantRepository: MerchantRepository
    private lateinit var assetsServiceClient: AssetsServiceClient
    private lateinit var presignedUrlHttpClient: PresignedUrlHttpClient
    private lateinit var merchantLogoUrlBuilder: MerchantLogoUrlBuilder

    private lateinit var service: MerchantLogoHydrationService

    @BeforeEach
    fun setUp() {
        merchantRepository = mock()
        assetsServiceClient = mock()
        presignedUrlHttpClient = mock()
        merchantLogoUrlBuilder = mock()
        whenever(merchantLogoUrlBuilder.hasToken()).thenReturn(true)
        whenever(merchantLogoUrlBuilder.buildLogoDevUrl("netflix.com")).thenReturn("https://internal.test/netflix.png")
        service =
            MerchantLogoHydrationService(
                merchantRepository = merchantRepository,
                assetsServiceClient = assetsServiceClient,
                presignedUrlHttpClient = presignedUrlHttpClient,
                merchantLogoUrlBuilder = merchantLogoUrlBuilder,
                merchantLogoNamespace = "institution-logos",
            )
    }

    @Test
    fun `hydrateForDomain skips when cache is fresh`() {
        whenever(merchantRepository.findLogoCacheByDomain("netflix.com")).thenReturn(
            MerchantLogoCacheEntry(
                domain = "netflix.com",
                assetId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
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
                assetId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                fetchedAt = staleFetchedAt,
            ),
        )
        whenever(presignedUrlHttpClient.get(any())).thenReturn(byteArrayOf(1, 2, 3))
        whenever(
            assetsServiceClient.createAssetUpload(
                namespace = eq("institution-logos"),
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
        verify(merchantRepository).upsertLogoCache(eq("netflix.com"), eq(UUID.fromString("00000000-0000-0000-0000-000000000010")), any())
    }

    @Test
    fun `hydrateForDomain uploads logo when no cache entry exists`() {
        whenever(merchantRepository.findLogoCacheByDomain("netflix.com")).thenReturn(null)
        whenever(presignedUrlHttpClient.get(any())).thenReturn(byteArrayOf(7, 8, 9))
        whenever(
            assetsServiceClient.createAssetUpload(
                namespace = eq("institution-logos"),
                mimeType = eq("image/png"),
                originalFilename = eq("netflix.com.png"),
                sizeBytes = eq(3L),
                delegatedBy = eq(null),
            ),
        ).thenReturn(
            CreateAssetUploadResult(
                id = UUID.fromString("00000000-0000-0000-0000-000000000099"),
                uploadUrl = "https://upload.example.com/new",
            ),
        )

        service.hydrateForDomain("netflix.com")

        verify(assetsServiceClient).confirmAssetUpload(UUID.fromString("00000000-0000-0000-0000-000000000099"), null)
        verify(merchantRepository).upsertLogoCache(eq("netflix.com"), eq(UUID.fromString("00000000-0000-0000-0000-000000000099")), any())
    }

    @Test
    fun `hydrateForDomain skips upload when logo bytes are empty`() {
        whenever(merchantRepository.findLogoCacheByDomain("netflix.com")).thenReturn(null)
        whenever(presignedUrlHttpClient.get(any())).thenReturn(byteArrayOf())

        service.hydrateForDomain("netflix.com")

        verify(assetsServiceClient, never()).createAssetUpload(any(), any(), any(), any(), any())
        verify(merchantRepository, never()).upsertLogoCache(any(), any(), any())
    }

    @Test
    fun `hydrateForDomainAsync delegates asynchronously when token exists`() {
        val latch = CountDownLatch(1)
        var capturedDomain: String? = null
        val asyncService =
            object : MerchantLogoHydrationService(
                merchantRepository = merchantRepository,
                assetsServiceClient = assetsServiceClient,
                presignedUrlHttpClient = presignedUrlHttpClient,
                merchantLogoUrlBuilder = merchantLogoUrlBuilder,
                merchantLogoNamespace = "institution-logos",
            ) {
                override fun hydrateForDomain(domain: String) {
                    capturedDomain = domain
                    latch.countDown()
                }
            }

        asyncService.hydrateForDomainAsync("netflix.com")

        val finished = latch.await(2, TimeUnit.SECONDS)
        assertTrue(finished, "Expected async hydration to execute")
        assertEquals("netflix.com", capturedDomain)
    }
}
