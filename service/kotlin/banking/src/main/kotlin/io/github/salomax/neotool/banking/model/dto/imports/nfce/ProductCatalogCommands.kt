package io.github.salomax.neotool.banking.model.dto.imports.nfce

import java.math.BigDecimal
import java.util.UUID

data class CreateProductCommand(
    val name: String,
    val ncmCode: String? = null,
    val defaultUnit: String? = null,
    val category: String? = null,
)

data class UpdateProductCommand(
    val name: String? = null,
    val ncmCode: String? = null,
    val defaultUnit: String? = null,
    val category: String? = null,
)

data class CreateProductVariantCommand(
    val productId: UUID,
    val name: String,
    val brand: String? = null,
    val ean: String? = null,
    val quantityValue: BigDecimal? = null,
    val quantityUnit: String? = null,
    val normalizedQuantityValue: BigDecimal? = null,
    val normalizedQuantityUnit: String? = null,
)

data class UpdateProductVariantCommand(
    val productId: UUID? = null,
    val name: String? = null,
    val brand: String? = null,
    val ean: String? = null,
    val quantityValue: BigDecimal? = null,
    val quantityUnit: String? = null,
    val normalizedQuantityValue: BigDecimal? = null,
    val normalizedQuantityUnit: String? = null,
)
