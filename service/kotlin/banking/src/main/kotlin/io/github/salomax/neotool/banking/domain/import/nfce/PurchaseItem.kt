package io.github.salomax.neotool.banking.domain.import.nfce

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PurchaseItem(
    val id: UUID? = null,
    val transactionId: UUID,
    val accountId: UUID,
    val productVariantId: UUID? = null,
    val sequence: Int,
    val rawDescription: String,
    val ncmCode: String? = null,
    val ean: String? = null,
    val issuerProductCode: String? = null,
    val quantity: BigDecimal,
    val unit: String,
    val unitPrice: BigDecimal,
    val totalPrice: BigDecimal,
    val discount: BigDecimal? = null,
    val effectivePrice: BigDecimal,
    val taxAmount: BigDecimal? = null,
    val createdAt: Instant,
)
