package io.github.salomax.neotool.banking.graphql.merchant

import io.github.salomax.neotool.banking.domain.transaction.Merchant
import io.github.salomax.neotool.banking.domain.transaction.MerchantRepository
import io.github.salomax.neotool.banking.service.transaction.MerchantService
import io.github.salomax.neotool.banking.domain.transaction.TopMerchantRow
import io.github.salomax.neotool.banking.graphql.merchant.MerchantDTO
import io.github.salomax.neotool.banking.graphql.merchant.TopMerchantDTO
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.util.UUID

/**
 * GraphQL resolver for Merchant queries and mutations.
 */
@Singleton
class MerchantResolver(
    private val merchantService: MerchantService,
    private val merchantRepository: MerchantRepository,
) {
    // ── Queries ───────────────────────────────────────────────────────────────

    fun merchants(principal: RequestPrincipal): List<MerchantDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return merchantService.listForAccount(accountId).map { it.toDTO(accountId) }
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

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun createMerchant(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): MerchantDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val name = input["name"] as? String ?: throw IllegalArgumentException("name is required")
        val domain = input["domain"] as? String
        val sector = input["sector"] as? String
        val merchant = merchantService.create(name, domain, sector, accountId)
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
            logoAsset = logoAssetId?.let { mapOf("__typename" to "Asset", "id" to it) },
        )
    }
}
