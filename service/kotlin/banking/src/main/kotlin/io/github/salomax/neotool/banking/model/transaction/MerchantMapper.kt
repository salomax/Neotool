package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.Merchant
import io.github.salomax.neotool.banking.model.domain.transaction.TransactionDescriptionNormalizer

object MerchantMapper {
    fun toDomain(entity: MerchantEntity): Merchant =
        Merchant(
            id = entity.id,
            name = entity.name,
            domain = entity.domain,
            sector = entity.sector,
            isSystem = entity.isSystem,
            accountId = entity.accountId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: Merchant): MerchantEntity =
        MerchantEntity(
            id = null,
            merchantName = TransactionDescriptionNormalizer.normalize(domain.name),
            name = domain.name,
            domain = domain.domain,
            sector = domain.sector,
            isSystem = domain.isSystem,
            accountId = domain.accountId,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
}
