package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.banking.domain.investment.Asset

/**
 * Maps between [Asset] domain model and [AssetEntity].
 */
object AssetMapper {
    fun toDomain(entity: AssetEntity): Asset =
        Asset(
            id = entity.id,
            ticker = entity.ticker,
            isinCode = entity.isinCode,
            name = entity.name,
            assetType = entity.assetType,
            exchange = entity.exchange,
            currencyCode = entity.currencyCode,
            issuerCnpj = entity.issuerCnpj,
            issuerName = entity.issuerName,
            fundCnpj = entity.fundCnpj,
            anbimaCategory = entity.anbimaCategory,
            anbimaClass = entity.anbimaClass,
            anbimaSubclass = entity.anbimaSubclass,
            priceFactor = entity.priceFactor,
            providerId = entity.providerId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: Asset): AssetEntity =
        AssetEntity(
            id = null,
            ticker = domain.ticker,
            isinCode = domain.isinCode,
            name = domain.name,
            assetType = domain.assetType,
            exchange = domain.exchange,
            currencyCode = domain.currencyCode,
            issuerCnpj = domain.issuerCnpj,
            issuerName = domain.issuerName,
            fundCnpj = domain.fundCnpj,
            anbimaCategory = domain.anbimaCategory,
            anbimaClass = domain.anbimaClass,
            anbimaSubclass = domain.anbimaSubclass,
            priceFactor = domain.priceFactor,
            providerId = domain.providerId,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )

    fun updateEntity(
        entity: AssetEntity,
        domain: Asset,
    ) {
        entity.name = domain.name
        entity.exchange = domain.exchange
        entity.currencyCode = domain.currencyCode
        entity.issuerCnpj = domain.issuerCnpj
        entity.issuerName = domain.issuerName
        entity.anbimaCategory = domain.anbimaCategory
        entity.anbimaClass = domain.anbimaClass
        entity.anbimaSubclass = domain.anbimaSubclass
        entity.priceFactor = domain.priceFactor
        entity.providerId = domain.providerId
        entity.updatedAt = domain.updatedAt
    }
}
