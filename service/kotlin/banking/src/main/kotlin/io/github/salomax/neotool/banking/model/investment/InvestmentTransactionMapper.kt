package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.banking.domain.investment.BrokerNote
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction

/**
 * Maps between [InvestmentTransaction]/[BrokerNote] domain models and their JPA entities.
 * BrokerNote is mapped as part of the transaction — never independently.
 */
object InvestmentTransactionMapper {
    fun toDomain(entity: InvestmentTransactionEntity): InvestmentTransaction =
        InvestmentTransaction(
            id = entity.id,
            holdingId = entity.holdingId,
            financialAccountId = entity.financialAccountId,
            accountId = entity.accountId,
            createdByUserId = entity.createdByUserId,
            direction = entity.direction,
            type = entity.type,
            typeAdditionalInfo = entity.typeAdditionalInfo,
            quantity = entity.quantity,
            unitPrice = entity.unitPrice,
            grossValue = entity.grossValue,
            netValue = entity.netValue,
            incomeTax = entity.incomeTax,
            iof = entity.iof,
            indexerPercentage = entity.indexerPercentage,
            agreedRate = entity.agreedRate,
            priceFactor = entity.priceFactor,
            tradeDate = entity.tradeDate,
            settlementDate = entity.settlementDate,
            source = entity.source,
            providerTransactionId = entity.providerTransactionId,
            importJobId = entity.importJobId,
            deletedAt = entity.deletedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            brokerNote = entity.brokerNote?.let(::toDomain),
        )

    fun toEntity(domain: InvestmentTransaction): InvestmentTransactionEntity {
        val entity = InvestmentTransactionEntity(
            id = null,
            holdingId = domain.holdingId,
            financialAccountId = domain.financialAccountId,
            accountId = domain.accountId,
            createdByUserId = domain.createdByUserId,
            direction = domain.direction,
            type = domain.type,
            typeAdditionalInfo = domain.typeAdditionalInfo,
            quantity = domain.quantity,
            unitPrice = domain.unitPrice,
            grossValue = domain.grossValue,
            netValue = domain.netValue,
            incomeTax = domain.incomeTax,
            iof = domain.iof,
            indexerPercentage = domain.indexerPercentage,
            agreedRate = domain.agreedRate,
            priceFactor = domain.priceFactor,
            tradeDate = domain.tradeDate,
            settlementDate = domain.settlementDate,
            source = domain.source,
            providerTransactionId = domain.providerTransactionId,
            importJobId = domain.importJobId,
            deletedAt = domain.deletedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
        domain.brokerNote?.let { note ->
            val noteEntity = toEntity(note)
            noteEntity.investmentTransaction = entity
            entity.brokerNote = noteEntity
        }
        return entity
    }

    // ── BrokerNote ─────────────────────────────────────────────────────────────

    private fun toDomain(entity: BrokerNoteEntity): BrokerNote =
        BrokerNote(
            investmentTransactionId = entity.id,
            accountId = entity.accountId,
            brokerNoteNumber = entity.brokerNoteNumber,
            grossValue = entity.grossValue,
            brokerageFee = entity.brokerageFee,
            clearingSettlementFee = entity.clearingSettlementFee,
            clearingRegistrationFee = entity.clearingRegistrationFee,
            stockExchangeAssetTradeNoticeFee = entity.stockExchangeAssetTradeNoticeFee,
            stockExchangeFee = entity.stockExchangeFee,
            clearingCustodyFee = entity.clearingCustodyFee,
            incomeTax = entity.incomeTax,
            taxes = entity.taxes,
            netValue = entity.netValue,
            createdAt = entity.createdAt,
        )

    private fun toEntity(domain: BrokerNote): BrokerNoteEntity =
        BrokerNoteEntity(
            id = domain.investmentTransactionId,
            accountId = domain.accountId,
            brokerNoteNumber = domain.brokerNoteNumber,
            grossValue = domain.grossValue,
            brokerageFee = domain.brokerageFee,
            clearingSettlementFee = domain.clearingSettlementFee,
            clearingRegistrationFee = domain.clearingRegistrationFee,
            stockExchangeAssetTradeNoticeFee = domain.stockExchangeAssetTradeNoticeFee,
            stockExchangeFee = domain.stockExchangeFee,
            clearingCustodyFee = domain.clearingCustodyFee,
            incomeTax = domain.incomeTax,
            taxes = domain.taxes,
            netValue = domain.netValue,
            createdAt = domain.createdAt,
        )
}
