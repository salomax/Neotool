package io.github.salomax.neotool.banking.model.account

import io.github.salomax.neotool.banking.domain.account.BankData
import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.domain.account.FinancialAccount

/**
 * Maps between [FinancialAccount] domain and [FinancialAccountEntity] (and CTI extension entities).
 */
object FinancialAccountMapper {
    fun toDomain(entity: FinancialAccountEntity): FinancialAccount {
        val bankData =
            entity.bankData?.let { b ->
                BankData(
                    branchCode = b.branchCode,
                    number = b.number,
                    checkDigit = b.checkDigit,
                    maskedNumber = b.maskedNumber,
                )
            }
        val cardData =
            entity.cardData?.let { c ->
                CardData(
                    maskedNumber = c.maskedNumber,
                    bin = c.bin,
                    cardNetwork = c.cardNetwork,
                    cardLevel = c.cardLevel,
                    creditLimit = c.creditLimit,
                    availableCreditLimit = c.availableCreditLimit,
                    minimumPaymentPercentage = c.minPaymentPercentage,
                    closingDay = c.closingDay,
                    dueDay = c.dueDay,
                )
            }
        return FinancialAccount(
            id = entity.id,
            accountId = entity.accountId,
            createdByUserId = entity.createdByUserId,
            type = entity.type,
            subtype = entity.subtype,
            name = entity.name,
            currencyCode = entity.currencyCode,
            includeInNetWorth = entity.includeInNetWorth,
            includeInDashboard = entity.includeInDashboard,
            issuerId = entity.issuerId,
            themeColor = entity.themeColor,
            logoKey = entity.logoKey,
            issuerName = entity.issuerName,
            issuerCountry = entity.issuerCountry,
            issuerCompeCode = entity.issuerCompeCode,
            issuerIspb = entity.issuerIspb,
            cardLastFourDigits = entity.cardLastFourDigits,
            bankData = bankData,
            cardData = cardData,
            connectionStatus = entity.connectionStatus,
            consentExpiresAt = entity.consentExpiresAt,
            lastSyncAt = entity.lastSyncAt,
            providerItemId = entity.providerItemId,
            deletedAt = entity.deletedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    /**
     * Builds base [FinancialAccountEntity] from domain (no bankData/cardData; attach those after persist when creating new).
     * Id is not set (DB generates uuidv7 on insert).
     */
    fun toEntity(domain: FinancialAccount): FinancialAccountEntity {
        return FinancialAccountEntity(
            id = null,
            accountId = domain.accountId,
            createdByUserId = domain.createdByUserId,
            type = domain.type,
            subtype = domain.subtype,
            name = domain.name,
            currencyCode = domain.currencyCode,
            includeInNetWorth = domain.includeInNetWorth,
            includeInDashboard = domain.includeInDashboard,
            issuerId = domain.issuerId,
            themeColor = domain.themeColor,
            logoKey = domain.logoKey,
            issuerName = domain.issuerName,
            issuerCountry = domain.issuerCountry,
            issuerCompeCode = domain.issuerCompeCode,
            issuerIspb = domain.issuerIspb,
            cardLastFourDigits = domain.cardLastFourDigits,
            connectionStatus = domain.connectionStatus,
            consentExpiresAt = domain.consentExpiresAt,
            lastSyncAt = domain.lastSyncAt,
            providerItemId = domain.providerItemId,
            deletedAt = domain.deletedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
    }

    /**
     * Builds [FinancialAccountBankDataEntity] for an existing account (call after base entity is persisted).
     */
    fun toBankDataEntity(
        data: BankData,
        account: FinancialAccountEntity,
    ): FinancialAccountBankDataEntity {
        val accountId =
            account.id ?: throw IllegalStateException(
                "Account must be persisted before attaching bank data",
            )
        return FinancialAccountBankDataEntity(
            accountId = accountId,
            account = account,
            branchCode = data.branchCode,
            number = data.number,
            checkDigit = data.checkDigit,
            maskedNumber = data.maskedNumber,
        )
    }

    /**
     * Builds [FinancialAccountCardDataEntity] for an existing account (call after base entity is persisted).
     */
    fun toCardDataEntity(
        data: CardData,
        account: FinancialAccountEntity,
    ): FinancialAccountCardDataEntity {
        val accountId =
            account.id ?: throw IllegalStateException(
                "Account must be persisted before attaching card data",
            )
        return FinancialAccountCardDataEntity(
            accountId = accountId,
            account = account,
            maskedNumber = data.maskedNumber,
            bin = data.bin,
            cardNetwork = data.cardNetwork,
            cardLevel = data.cardLevel,
            creditLimit = data.creditLimit,
            availableCreditLimit = data.availableCreditLimit,
            minPaymentPercentage = data.minimumPaymentPercentage,
            closingDay = data.closingDay,
            dueDay = data.dueDay,
        )
    }

    /**
     * Updates existing entity fields from domain (for update path). Does not replace bankData/cardData.
     */
    fun updateEntity(
        entity: FinancialAccountEntity,
        domain: FinancialAccount,
    ) {
        entity.accountId = domain.accountId
        entity.createdByUserId = domain.createdByUserId
        entity.type = domain.type
        entity.subtype = domain.subtype
        entity.name = domain.name
        entity.currencyCode = domain.currencyCode
        entity.includeInNetWorth = domain.includeInNetWorth
        entity.includeInDashboard = domain.includeInDashboard
        entity.issuerId = domain.issuerId
        entity.themeColor = domain.themeColor
        entity.logoKey = domain.logoKey
        entity.issuerName = domain.issuerName
        entity.issuerCountry = domain.issuerCountry
        entity.issuerCompeCode = domain.issuerCompeCode
        entity.issuerIspb = domain.issuerIspb
        entity.cardLastFourDigits = domain.cardLastFourDigits
        entity.connectionStatus = domain.connectionStatus
        entity.consentExpiresAt = domain.consentExpiresAt
        entity.lastSyncAt = domain.lastSyncAt
        entity.providerItemId = domain.providerItemId
        entity.deletedAt = domain.deletedAt
        entity.updatedAt = domain.updatedAt
    }
}
