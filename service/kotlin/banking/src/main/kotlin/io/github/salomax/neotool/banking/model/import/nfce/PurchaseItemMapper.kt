package io.github.salomax.neotool.banking.model.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.PurchaseItem

object PurchaseItemMapper {
    fun toDomain(entity: PurchaseItemEntity): PurchaseItem =
        PurchaseItem(
            id = entity.id,
            transactionId = entity.transactionId,
            accountId = entity.accountId,
            productVariantId = entity.productVariantId,
            sequence = entity.sequence,
            rawDescription = entity.rawDescription,
            ncmCode = entity.ncmCode,
            ean = entity.ean,
            issuerProductCode = entity.issuerProductCode,
            quantity = entity.quantity,
            unit = entity.unit,
            unitPrice = entity.unitPrice,
            totalPrice = entity.totalPrice,
            discount = entity.discount,
            effectivePrice = entity.effectivePrice,
            taxAmount = entity.taxAmount,
            createdAt = entity.createdAt,
        )

    fun toEntity(domain: PurchaseItem): PurchaseItemEntity =
        PurchaseItemEntity(
            id = domain.id,
            transactionId = domain.transactionId,
            accountId = domain.accountId,
            productVariantId = domain.productVariantId,
            sequence = domain.sequence,
            rawDescription = domain.rawDescription,
            ncmCode = domain.ncmCode,
            ean = domain.ean,
            issuerProductCode = domain.issuerProductCode,
            quantity = domain.quantity,
            unit = domain.unit,
            unitPrice = domain.unitPrice,
            totalPrice = domain.totalPrice,
            discount = domain.discount,
            effectivePrice = domain.effectivePrice,
            taxAmount = domain.taxAmount,
            createdAt = domain.createdAt,
        )
}
