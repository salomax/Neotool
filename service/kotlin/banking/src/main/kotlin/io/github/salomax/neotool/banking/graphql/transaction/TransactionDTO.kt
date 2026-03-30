package io.github.salomax.neotool.banking.graphql.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.PatternType
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.graphql.merchant.MerchantDTO
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class TransactionCategoryDTO(
    val id: UUID,
    val parentId: UUID?,
    val name: String,
    val applicableTo: CategoryApplicableTo,
    val icon: String?,
    val color: String?,
    val isSystem: Boolean,
    val sortOrder: Int,
)

data class TransactionRuleDTO(
    val id: UUID,
    val categoryId: UUID?,
    val category: TransactionCategoryDTO?,
    val merchantId: UUID?,
    val merchant: MerchantDTO?,
    val descriptionOverride: String?,
    val tagsOverride: List<String>?,
    val pattern: String,
    val patternType: PatternType,
    val applicableTo: CategoryApplicableTo,
    val priority: Int,
    val isSystem: Boolean,
    val isImplicit: Boolean,
    val matchCount: Int,
    val position: Int,
    val createdAt: Instant,
)

data class TransactionRuleEdgeDTO(
    val node: TransactionRuleDTO,
    val cursor: String,
)

data class TransactionRuleConnectionDTO(
    val edges: List<TransactionRuleEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int?,
)

data class CategoryTotalDTO(
    val category: TransactionCategoryDTO?,
    val uncategorized: Boolean,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentage: Double,
)

data class TagTotalDTO(
    val tag: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentage: Double,
)

data class TransactionDTO(
    val id: UUID,
    val financialAccountId: UUID,
    val belongsToUserId: UUID?,
    val belongsToName: String?,
    val creditDebitType: CreditDebitType,
    val type: TransactionType,
    val status: TransactionStatus,
    val source: TransactionSource,
    val amount: Double,
    val currencyCode: String,
    val amountBrl: Double?,
    val date: LocalDate,
    /** Exact date-time of the transaction when provided (e.g. by Open Finance). Null when only date is known. */
    val transactionDatetime: Instant?,
    val description: String,
    val notes: String?,
    val tags: List<String>,
    val category: TransactionCategoryDTO?,
    /** How the category was assigned: MANUAL, RULE, IMPORTED, AI. Null when uncategorized. */
    val categorySource: CategorySource?,
    val merchant: MerchantDTO?,
    // Counterparty
    val counterpartyName: String?,
    val counterpartyDocument: String?,
    val counterpartyBankCode: String?,
    val counterpartyAccountNumber: String?,
    val paymentReason: String?,
    // Transfer
    val transferPeerTransactionId: UUID?,
    // Installments
    val installmentNumber: Int?,
    val totalInstallments: Int?,
    // Card
    val billId: UUID?,
    val cardIdentifier: String?,
    val payeeMcc: Int?,
    val billPostDate: LocalDate?,
    val appliedRuleIds: List<UUID>,
    // Audit
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TransactionBelongsToOptionDTO(
    val belongsToUserId: UUID?,
    val belongsToName: String?,
)

data class TransactionTagOptionDTO(
    val tag: String,
)

/**
 * Relay pagination DTOs for transactions.
 */
data class PageInfoDTO(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String?,
    val endCursor: String?,
)

data class TransactionEdgeDTO(
    val node: TransactionDTO,
    val cursor: String,
)

data class TransactionConnectionDTO(
    val edges: List<TransactionEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int?,
)

data class SimilarTransactionEditPreviewDTO(
    val similarCount: Int,
    val matchDescription: String,
    val supportsFuturePropagation: Boolean,
)

data class SimilarTransactionEditResultDTO(
    val affectedCount: Int,
    val futureRuleCreated: Boolean,
)

data class CreateTransactionPayloadDTO(
    val transaction: TransactionDTO,
    val appliedRules: List<TransactionRuleDTO>?,
)
