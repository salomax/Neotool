package io.github.salomax.neotool.banking.model.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.ProductVariant

object ProductVariantMapper {
    fun toDomain(entity: ProductVariantEntity): ProductVariant =
        ProductVariant(
            id = entity.id,
            productId = entity.productId,
            accountId = entity.accountId,
            name = entity.name,
            brand = entity.brand,
            ean = entity.ean,
            quantityValue = entity.quantityValue,
            quantityUnit = entity.quantityUnit,
            normalizedQuantityValue = entity.normalizedQuantityValue,
            normalizedQuantityUnit = entity.normalizedQuantityUnit,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: ProductVariant): ProductVariantEntity =
        ProductVariantEntity(
            id = domain.id,
            productId = domain.productId,
            accountId = domain.accountId,
            name = domain.name,
            brand = domain.brand,
            ean = domain.ean,
            quantityValue = domain.quantityValue,
            quantityUnit = domain.quantityUnit,
            normalizedQuantityValue = domain.normalizedQuantityValue,
            normalizedQuantityUnit = domain.normalizedQuantityUnit,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
}
