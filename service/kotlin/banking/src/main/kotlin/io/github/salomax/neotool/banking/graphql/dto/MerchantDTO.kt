package io.github.salomax.neotool.banking.graphql.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * GraphQL response DTO for [Merchant].
 *
 * [logoAsset] is a federation stub — when the domain has a cached logo it holds
 * `{ __typename: "Asset", id: assetId }` so Apollo Router resolves the full
 * Asset from the assets subgraph. Null until a logo cache entry exists for the domain.
 */
data class MerchantDTO(
    val id: UUID,
    val name: String,
    val domain: String?,
    val sector: String?,
    val isSystem: Boolean,
    /** Federation reference resolved by [BankingWiringFactory]. */
    val logoAsset: Map<String, String>?,
)

/**
 * GraphQL response DTO for a top-merchant row (from v_top_merchants view).
 */
data class TopMerchantDTO(
    val merchant: MerchantDTO,
    val transactionCount: Long,
    val totalAmount: Double,
    val lastUsedAt: LocalDate,
)
