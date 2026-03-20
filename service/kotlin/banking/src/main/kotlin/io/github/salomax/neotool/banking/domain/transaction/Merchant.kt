package io.github.salomax.neotool.banking.domain.transaction

import java.time.Instant
import java.util.UUID

/**
 * Domain model for banking.merchants.
 *
 * - [isSystem] = true / [accountId] = null  → global system entry (seeded from CSV).
 * - [isSystem] = false / [accountId] = UUID → user-scoped custom merchant.
 *
 * Logo is NOT stored here; it is resolved at query time via [domain] →
 * merchant_logo_cache → Asset service (shared across merchants with the same domain).
 */
data class Merchant(
    val id: UUID? = null,
    val name: String,
    val domain: String? = null,
    val sector: String? = null,
    val isSystem: Boolean = false,
    val accountId: UUID? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

/**
 * Row returned by the v_top_merchants view.
 */
data class TopMerchantRow(
    val merchantId: UUID,
    val merchantName: String,
    val merchantDomain: String?,
    val merchantSector: String?,
    val transactionCount: Long,
    val totalAmount: java.math.BigDecimal,
    val lastUsedAt: java.time.LocalDate,
)
