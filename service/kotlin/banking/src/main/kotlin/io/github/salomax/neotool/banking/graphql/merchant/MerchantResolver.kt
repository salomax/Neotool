package io.github.salomax.neotool.banking.graphql.merchant

import io.github.salomax.neotool.banking.domain.transaction.Merchant
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.service.transaction.MerchantLogoHydrationService
import io.github.salomax.neotool.banking.service.transaction.MerchantService
import io.github.salomax.neotool.banking.service.transaction.MerchantCursorCodec
import io.github.salomax.neotool.banking.service.transaction.MerchantWebsiteNormalizer
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

/**
 * GraphQL resolver for Merchant queries and mutations.
 */
@Singleton
class MerchantResolver(
    private val merchantService: MerchantService,
    private val merchantRepository: MerchantRepository,
    private val merchantLogoHydrationService: MerchantLogoHydrationService,
) {
    private val logger = KotlinLogging.logger {}

    // ── Queries ───────────────────────────────────────────────────────────────

    fun merchants(
        search: String?,
        after: String?,
        first: Int?,
        principal: RequestPrincipal,
    ): MerchantConnectionDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val page = merchantService.search(accountId, search, after, first)
        val edges =
            page.items.map { merchant ->
                MerchantEdgeDTO(
                    node = merchant.toDTO(accountId),
                    cursor = MerchantCursorCodec.encode(merchant.name, requireNotNull(merchant.id)),
                )
            }
        return MerchantConnectionDTO(
            edges = edges,
            pageInfo =
                MerchantPageInfoDTO(
                    hasNextPage = page.hasNextPage,
                    hasPreviousPage = page.hasPreviousPage,
                    startCursor = page.startCursor,
                    endCursor = page.endCursor,
                ),
            totalCount = page.totalCount,
        )
    }

    fun topMerchants(
        financialAccountId: String?,
        year: Int?,
        month: Int?,
        limit: Int?,
        principal: RequestPrincipal,
    ): List<TopMerchantDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val rows =
            merchantService.topMerchants(
                accountId = accountId,
                financialAccountId = financialAccountId?.let(UUID::fromString),
                year = year,
                month = month,
                limit = limit ?: 10,
            )
        // Batch-load merchants for the result rows (PK lookup per row — small result set)
        return rows.map { row ->
            val merchant =
                merchantRepository.findById(row.merchantId)
                    ?: Merchant(
                        id = row.merchantId,
                        name = row.merchantName,
                        domain = row.merchantDomain,
                        sector = row.merchantSector,
                    )
            TopMerchantDTO(
                merchant = merchant.toDTO(accountId),
                transactionCount = row.transactionCount,
                totalAmount = row.totalAmount.toDouble(),
                lastUsedAt = row.lastUsedAt,
            )
        }
    }

    fun merchantLogoPreview(
        domain: String,
        principal: RequestPrincipal,
    ): Map<String, String>? {
        requireNotNull(principal.currentAccountId) { "No active account in token" }
        val normalizedDomain = MerchantWebsiteNormalizer.normalizeDomain(domain) ?: return null

        val cachedAssetId = merchantRepository.findLogoAssetIdByDomain(normalizedDomain)
        if (cachedAssetId != null) {
            return mapOf("__typename" to "Asset", "id" to cachedAssetId.toString())
        }

        runCatching { merchantLogoHydrationService.hydrateForDomain(normalizedDomain) }
            .onFailure { error ->
                logger.warn(error) { "Merchant logo preview hydration failed for domain=$normalizedDomain" }
            }
        val hydratedAssetId = merchantRepository.findLogoAssetIdByDomain(normalizedDomain) ?: return null
        return mapOf("__typename" to "Asset", "id" to hydratedAssetId.toString())
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun createMerchant(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): MerchantDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val name = input["name"] as? String ?: throw IllegalArgumentException("name is required")
        val website = input["website"] as? String
        val sector = input["sector"] as? String
        val merchant = merchantService.create(name, website, sector, accountId)
        return merchant.toDTO(accountId)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Converts a [Merchant] to a [MerchantDTO], resolving the logo asset from cache.
     * [accountId] is used to build the logo cache key if needed (no-op here — cache is by domain).
     */
    fun Merchant.toDTO(accountId: UUID): MerchantDTO {
        val logoAssetId = domain?.let { merchantRepository.findLogoAssetIdByDomain(it) }
        return MerchantDTO(
            id = id!!,
            name = name,
            domain = domain,
            sector = sector,
            isSystem = isSystem,
            isOwned = this.accountId == accountId,
            logoAsset = logoAssetId?.let { mapOf("__typename" to "Asset", "id" to it.toString()) },
        )
    }
}
