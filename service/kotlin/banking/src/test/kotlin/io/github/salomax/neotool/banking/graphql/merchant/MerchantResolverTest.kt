package io.github.salomax.neotool.banking.graphql.merchant

import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.service.transaction.MerchantLogoHydrationService
import io.github.salomax.neotool.banking.service.transaction.MerchantService
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class MerchantResolverTest {
    private val merchantService: MerchantService = mock()
    private val merchantRepository: MerchantRepository = mock()
    private val merchantLogoHydrationService: MerchantLogoHydrationService = mock()

    private val resolver =
        MerchantResolver(
            merchantService = merchantService,
            merchantRepository = merchantRepository,
            merchantLogoHydrationService = merchantLogoHydrationService,
        )

    private fun principal(accountId: UUID = UUID.randomUUID()): RequestPrincipal =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = UUID.randomUUID(),
            serviceId = null,
            token = "token",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )

    @Test
    fun `merchantLogoPreview returns cached asset reference when available`() {
        val assetId = UUID.fromString("00000000-0000-0000-0000-000000000123")
        whenever(merchantRepository.findLogoAssetIdByDomain("netflix.com")).thenReturn(assetId)

        val result = resolver.merchantLogoPreview("https://www.netflix.com/plans", principal())

        assertThat(result).isEqualTo(mapOf("__typename" to "Asset", "id" to assetId.toString()))
        verify(merchantLogoHydrationService, never()).hydrateForDomain(any())
    }

    @Test
    fun `merchantLogoPreview hydrates then returns asset reference when cache is missing`() {
        val assetId = UUID.fromString("00000000-0000-0000-0000-000000000456")
        whenever(merchantRepository.findLogoAssetIdByDomain("spotify.com")).thenReturn(null, assetId)

        val result = resolver.merchantLogoPreview("spotify.com", principal())

        assertThat(result).isEqualTo(mapOf("__typename" to "Asset", "id" to assetId.toString()))
        verify(merchantLogoHydrationService).hydrateForDomain(eq("spotify.com"))
    }

    @Test
    fun `merchantLogoPreview returns null for invalid domain`() {
        val result = resolver.merchantLogoPreview("invalid", principal())

        assertThat(result).isNull()
        verify(merchantRepository, never()).findLogoAssetIdByDomain(any())
    }
}
