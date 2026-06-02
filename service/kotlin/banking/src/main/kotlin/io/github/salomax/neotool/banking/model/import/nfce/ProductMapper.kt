package io.github.salomax.neotool.banking.model.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.Product

object ProductMapper {
    fun toDomain(entity: ProductEntity): Product =
        Product(
            id = entity.id,
            accountId = entity.accountId,
            name = entity.name,
            ncmCode = entity.ncmCode,
            defaultUnit = entity.defaultUnit,
            category = entity.category,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: Product): ProductEntity =
        ProductEntity(
            id = domain.id,
            accountId = domain.accountId,
            name = domain.name,
            ncmCode = domain.ncmCode,
            defaultUnit = domain.defaultUnit,
            category = domain.category,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
}
