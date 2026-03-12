package io.github.salomax.neotool.assets.graphql.resolver

import io.github.salomax.neotool.assets.domain.Asset
import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.domain.AssetVisibility
import io.github.salomax.neotool.assets.service.AssetService
import io.github.salomax.neotool.assets.storage.BucketResolver
import io.github.salomax.neotool.common.security.principal.PrincipalType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("AssetQueryResolver Unit Tests")
class AssetQueryResolverTest {
    private lateinit var assetService: AssetService
    private lateinit var bucketResolver: BucketResolver
    private lateinit var resolver: AssetQueryResolver

    @BeforeEach
    fun setUp() {
        assetService = mock()
        bucketResolver = mock()
        resolver = AssetQueryResolver(assetService, bucketResolver)
    }

    @Test
    fun `should forward principal type to service`() {
        val assetId = UUID.randomUUID()
        val requesterId = "service-banking"
        whenever(bucketResolver.getPublicBaseUrl()).thenReturn("https://cdn.test")
        whenever(
            assetService.getAsset(
                eq(assetId),
                eq(requesterId),
                eq(PrincipalType.SERVICE),
            ),
        ).thenReturn(testAsset(assetId))

        val result = resolver.asset(assetId.toString(), requesterId, principalType = PrincipalType.SERVICE)

        assertThat(result).isNotNull
        verify(assetService).getAsset(assetId, requesterId, PrincipalType.SERVICE)
    }

    @Test
    fun `should use user principal by default`() {
        val assetId = UUID.randomUUID()
        val requesterId = "user-123"
        whenever(bucketResolver.getPublicBaseUrl()).thenReturn("https://cdn.test")
        whenever(
            assetService.getAsset(
                eq(assetId),
                eq(requesterId),
                eq(PrincipalType.USER),
            ),
        ).thenReturn(testAsset(assetId))

        resolver.asset(assetId.toString(), requesterId, principalType = PrincipalType.USER)

        verify(assetService).getAsset(assetId, requesterId, PrincipalType.USER)
    }

    private fun testAsset(id: UUID): Asset {
        val now = Instant.now()
        return Asset(
            id = id,
            ownerId = "owner-1",
            namespace = "transaction-imports",
            visibility = AssetVisibility.PRIVATE,
            storageKey = "transaction-imports/owner-1/$id",
            storageRegion = "us-east-1",
            storageBucket = "neotool-assets-private",
            mimeType = "application/pdf",
            sizeBytes = 1234L,
            checksum = null,
            originalFilename = "bill.pdf",
            uploadUrl = null,
            uploadExpiresAt = null,
            publicUrl = null,
            status = AssetStatus.READY,
            idempotencyKey = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
    }
}
