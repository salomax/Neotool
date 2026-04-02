package io.github.salomax.neotool.banking.model.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.ProductAlias
import io.github.salomax.neotool.banking.domain.import.nfce.ProductAliasSource

object ProductAliasMapper {
    fun toDomain(entity: ProductAliasEntity): ProductAlias =
        ProductAlias(
            id = entity.id,
            productVariantId = entity.productVariantId,
            accountId = entity.accountId,
            rawDescription = entity.rawDescription,
            source = ProductAliasSource.valueOf(entity.source),
            confidence = entity.confidence,
            confirmed = entity.confirmed,
            createdAt = entity.createdAt,
        )

    fun toEntity(domain: ProductAlias): ProductAliasEntity =
        ProductAliasEntity(
            id = domain.id,
            productVariantId = domain.productVariantId,
            accountId = domain.accountId,
            rawDescription = domain.rawDescription,
            source = domain.source.name,
            confidence = domain.confidence,
            confirmed = domain.confirmed,
            createdAt = domain.createdAt,
        )
}
