package io.github.salomax.neotool.banking.model.transaction

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.salomax.neotool.banking.domain.transaction.AppliedRuleAuditEntry
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Maps between domain models and JPA entities for transactions and categories.
 */
object TransactionMapper {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    private data class StoredAppliedRuleAuditEntry(
        val ruleId: UUID,
        val appliedAt: Instant,
    )
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
            merchantId = entity.merchantId,
            counterpartyName = entity.counterpartyName,
            counterpartyDocument = entity.counterpartyDocument,
            counterpartyBankCode = entity.counterpartyBankCode,
            counterpartyAccountNumber = entity.counterpartyAccountNumber,
            paymentReason = entity.paymentReason,
            transferPeerTransactionId = entity.transferPeerTransactionId,
            investmentTransactionId = entity.investmentTransactionId,
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
            sourceData = entity.sourceData?.let { objectMapper.readValue<Map<String, Any?>>(it) },
            appliedRuleHistory = parseAppliedRuleHistory(entity.appliedRuleIds, entity.createdAt),
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
            merchantId = domain.merchantId,
            counterpartyName = domain.counterpartyName,
            counterpartyDocument = domain.counterpartyDocument,
            counterpartyBankCode = domain.counterpartyBankCode,
            counterpartyAccountNumber = domain.counterpartyAccountNumber,
            paymentReason = domain.paymentReason,
            transferPeerTransactionId = domain.transferPeerTransactionId,
            investmentTransactionId = domain.investmentTransactionId,
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
            sourceData = domain.sourceData?.let { objectMapper.writeValueAsString(it) },
            appliedRuleIds = serializeAppliedRuleHistory(domain.appliedRuleHistory),
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
        entity.descriptionRaw = domain.descriptionRaw
        entity.notes = domain.notes
        entity.categoryId = domain.categoryId
        entity.categorySource = domain.categorySource?.name
        entity.merchantId = domain.merchantId
        entity.belongsToUserId = domain.belongsToUserId
        entity.belongsToName = domain.belongsToName
        entity.counterpartyName = domain.counterpartyName
        entity.counterpartyDocument = domain.counterpartyDocument
        entity.counterpartyBankCode = domain.counterpartyBankCode
        entity.counterpartyAccountNumber = domain.counterpartyAccountNumber
        entity.paymentReason = domain.paymentReason
        entity.transferPeerTransactionId = domain.transferPeerTransactionId
        entity.investmentTransactionId = domain.investmentTransactionId
        entity.installmentNumber = domain.installmentNumber?.toShort()
        entity.totalInstallments = domain.totalInstallments?.toShort()
        entity.billId = domain.billId
        entity.payeeMcc = domain.payeeMcc
        entity.cardIdentifier = domain.cardIdentifier
        entity.providerTransactionId = domain.providerTransactionId
        entity.providerAccountId = domain.providerAccountId
        entity.importFingerprint = domain.importFingerprint
        entity.importJobId = domain.importJobId
        entity.sourceData =
            domain.sourceData
                ?.let { source -> objectMapper.writeValueAsString(source) }
        entity.appliedRuleIds = serializeAppliedRuleHistory(domain.appliedRuleHistory)
        entity.deletedAt = domain.deletedAt
        entity.updatedAt = domain.updatedAt
    }

    private fun parseAppliedRuleHistory(
        raw: String?,
        fallbackAppliedAt: Instant,
    ): List<AppliedRuleAuditEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = objectMapper.readTree(raw)
        if (!root.isArray) return emptyList()

        return root.mapNotNull { node ->
            when {
                node.isTextual -> {
                    val ruleId = runCatching { UUID.fromString(node.asText()) }.getOrNull() ?: return@mapNotNull null
                    AppliedRuleAuditEntry(ruleId = ruleId, appliedAt = fallbackAppliedAt)
                }

                node.isObject -> {
                    val ruleIdText = node.path("ruleId").asText(null) ?: return@mapNotNull null
                    val ruleId = runCatching { UUID.fromString(ruleIdText) }.getOrNull() ?: return@mapNotNull null
                    val appliedAt = parseAppliedAtNode(node.path("appliedAt")) ?: return@mapNotNull null
                    AppliedRuleAuditEntry(ruleId = ruleId, appliedAt = appliedAt)
                }

                else -> null
            }
        }
    }

    private fun parseAppliedAtNode(node: com.fasterxml.jackson.databind.JsonNode): Instant? {
        if (node.isMissingNode || node.isNull) return null
        if (node.isTextual) {
            return runCatching { Instant.parse(node.asText()) }.getOrNull()
        }
        if (node.isNumber) {
            val decimal = runCatching { node.decimalValue() }.getOrNull() ?: return null
            val seconds = decimal.setScale(0, RoundingMode.DOWN).longValueExact()
            val nanos =
                decimal
                    .subtract(BigDecimal.valueOf(seconds))
                    .movePointRight(9)
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact()
            return Instant.ofEpochSecond(seconds, nanos.toLong())
        }
        return null
    }

    private fun serializeAppliedRuleHistory(history: List<AppliedRuleAuditEntry>): String? =
        history
            .takeIf { it.isNotEmpty() }
            ?.map { StoredAppliedRuleAuditEntry(ruleId = it.ruleId, appliedAt = it.appliedAt) }
            ?.let(objectMapper::writeValueAsString)
}
