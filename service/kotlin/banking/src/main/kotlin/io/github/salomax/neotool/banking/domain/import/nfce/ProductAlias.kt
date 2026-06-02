package io.github.salomax.neotool.banking.domain.import.nfce

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class ProductAliasSource {
    MANUAL,
    AI_SUGGESTED,
    EAN_MATCH,
    HEURISTIC,
}

data class ProductAlias(
    val id: UUID? = null,
    val productVariantId: UUID,
    val accountId: UUID,
    val rawDescription: String,
    val source: ProductAliasSource,
    val confidence: BigDecimal? = null,
    val confirmed: Boolean = false,
    val createdAt: Instant = Instant.now(),
)
