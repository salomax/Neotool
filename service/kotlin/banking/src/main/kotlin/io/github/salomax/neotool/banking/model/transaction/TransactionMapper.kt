package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory

/**
 * Maps between domain models and JPA entities for transactions and categories.
 */
object TransactionMapper {
    // ── TransactionCategory ──────────────────────────────────────────────────

    fun toDomain(entity: TransactionCategoryEntity): TransactionCategory =
        TransactionCategory(
            id = entity.id,
            accountId = entity.accountId,
            parentId = entity.parentId,
            name = entity.name,
            applicableTo = entity.applicableTo,
            icon = entity.icon,
            color = entity.color,
            isSystem = entity.isSystem,
            sortOrder = entity.sortOrder,
            deletedAt = entity.deletedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: TransactionCategory): TransactionCategoryEntity =
        TransactionCategoryEntity(
            id = null,
            accountId = domain.accountId,
            parentId = domain.parentId,
            name = domain.name,
            applicableTo = domain.applicableTo,
            icon = domain.icon,
            color = domain.color,
            isSystem = domain.isSystem,
            sortOrder = domain.sortOrder,
            deletedAt = domain.deletedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )

    // ── Transaction ──────────────────────────────────────────────────────────

    fun toDomain(entity: TransactionEntity): Transaction =
        Transaction(
            id = entity.id,
            financialAccountId = entity.financialAccountId,
            accountId = entity.accountId,
            createdByUserId = entity.createdByUserId,
            belongsToUserId = entity.belongsToUserId,
            belongsToName = entity.belongsToName,
            creditDebitType = entity.creditDebitType,
            type = entity.type,
            status = entity.status,
            source = entity.source,
            amount = entity.amount,
            currencyCode = entity.currencyCode,
            amountBrl = entity.amountBrl,
            date = entity.date,
            transactionDatetime = entity.transactionDatetime,
            description = entity.description,
            descriptionRaw = entity.descriptionRaw,
            notes = entity.notes,
            categoryId = entity.categoryId,
            categorySource = entity.categorySource?.let { CategorySource.valueOf(it) },
            counterpartyName = entity.counterpartyName,
            counterpartyDocument = entity.counterpartyDocument,
            counterpartyBankCode = entity.counterpartyBankCode,
            counterpartyAccountNumber = entity.counterpartyAccountNumber,
            paymentReason = entity.paymentReason,
            transferPeerTransactionId = entity.transferPeerTransactionId,
            billId = entity.billId,
            installmentNumber = entity.installmentNumber?.toInt(),
            totalInstallments = entity.totalInstallments?.toInt(),
            payeeMcc = entity.payeeMcc,
            cardIdentifier = entity.cardIdentifier,
            billPostDate = entity.billPostDate,
            providerTransactionId = entity.providerTransactionId,
            providerAccountId = entity.providerAccountId,
            importFingerprint = entity.importFingerprint,
            importJobId = entity.importJobId,
            deletedAt = entity.deletedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: Transaction): TransactionEntity =
        TransactionEntity(
            id = null,
            financialAccountId = domain.financialAccountId,
            accountId = domain.accountId,
            createdByUserId = domain.createdByUserId,
            belongsToUserId = domain.belongsToUserId,
            belongsToName = domain.belongsToName,
            creditDebitType = domain.creditDebitType,
            type = domain.type,
            status = domain.status,
            source = domain.source,
            amount = domain.amount,
            currencyCode = domain.currencyCode,
            amountBrl = domain.amountBrl,
            date = domain.date,
            transactionDatetime = domain.transactionDatetime,
            description = domain.description,
            descriptionRaw = domain.descriptionRaw,
            notes = domain.notes,
            categoryId = domain.categoryId,
            categorySource = domain.categorySource?.name,
            counterpartyName = domain.counterpartyName,
            counterpartyDocument = domain.counterpartyDocument,
            counterpartyBankCode = domain.counterpartyBankCode,
            counterpartyAccountNumber = domain.counterpartyAccountNumber,
            paymentReason = domain.paymentReason,
            transferPeerTransactionId = domain.transferPeerTransactionId,
            billId = domain.billId,
            installmentNumber = domain.installmentNumber?.toShort(),
            totalInstallments = domain.totalInstallments?.toShort(),
            payeeMcc = domain.payeeMcc,
            cardIdentifier = domain.cardIdentifier,
            billPostDate = domain.billPostDate,
            providerTransactionId = domain.providerTransactionId,
            providerAccountId = domain.providerAccountId,
            importFingerprint = domain.importFingerprint,
            importJobId = domain.importJobId,
            deletedAt = domain.deletedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )

    fun updateEntity(
        entity: TransactionEntity,
        domain: Transaction,
    ) {
        entity.creditDebitType = domain.creditDebitType
        entity.type = domain.type
        entity.status = domain.status
        entity.amount = domain.amount
        entity.currencyCode = domain.currencyCode
        entity.amountBrl = domain.amountBrl
        entity.date = domain.date
        entity.transactionDatetime = domain.transactionDatetime
        entity.description = domain.description
        entity.notes = domain.notes
        entity.categoryId = domain.categoryId
        entity.categorySource = domain.categorySource?.name
        entity.belongsToUserId = domain.belongsToUserId
        entity.belongsToName = domain.belongsToName
        entity.counterpartyName = domain.counterpartyName
        entity.counterpartyDocument = domain.counterpartyDocument
        entity.counterpartyBankCode = domain.counterpartyBankCode
        entity.counterpartyAccountNumber = domain.counterpartyAccountNumber
        entity.paymentReason = domain.paymentReason
        entity.installmentNumber = domain.installmentNumber?.toShort()
        entity.totalInstallments = domain.totalInstallments?.toShort()
        entity.billId = domain.billId
        entity.payeeMcc = domain.payeeMcc
        entity.cardIdentifier = domain.cardIdentifier
        entity.importFingerprint = domain.importFingerprint
        entity.importJobId = domain.importJobId
        entity.deletedAt = domain.deletedAt
        entity.updatedAt = domain.updatedAt
    }
}
