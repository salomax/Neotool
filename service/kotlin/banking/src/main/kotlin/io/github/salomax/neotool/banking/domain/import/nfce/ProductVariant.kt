package io.github.salomax.neotool.banking.domain.import.nfce

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ProductVariant(
    val id: UUID? = null,
    val productId: UUID,
    val accountId: UUID,
    val name: String,
    val brand: String? = null,
    val ean: String? = null,
    val quantityValue: BigDecimal? = null,
    val quantityUnit: String? = null,
    val normalizedQuantityValue: BigDecimal? = null,
    val normalizedQuantityUnit: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
