package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA entity for banking.transactions.
 * amount is always positive; direction is carried by creditDebitType.
 */
@Entity
@Table(name = "transactions", schema = "banking")
open class TransactionEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    // Ownership
    @Column(name = "financial_account_id", nullable = false)
    open var financialAccountId: UUID,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "created_by_user_id", nullable = false)
    open var createdByUserId: UUID,
    @Column(name = "belongs_to_user_id")
    open var belongsToUserId: UUID? = createdByUserId,
    @Column(name = "belongs_to_name")
    open var belongsToName: String? = null,
    // Classification
    @Enumerated(EnumType.STRING)
    @Column(name = "credit_debit_type", nullable = false, length = 6)
    open var creditDebitType: CreditDebitType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    open var type: TransactionType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var status: TransactionStatus = TransactionStatus.POSTED,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var source: TransactionSource = TransactionSource.MANUAL,
    // Amount
    @Column(nullable = false, precision = 18, scale = 4)
    open var amount: BigDecimal,
    @Column(name = "currency_code", nullable = false, length = 3)
    open var currencyCode: String = "BRL",
    @Column(name = "amount_brl", precision = 18, scale = 4)
    open var amountBrl: BigDecimal? = null,
    // Dates
    @Column(nullable = false)
    open var date: LocalDate,
    @Column(name = "transaction_datetime")
    open var transactionDatetime: Instant? = null,
    // Description
    @Column(nullable = false)
    open var description: String,
    @Column(name = "description_raw")
    open var descriptionRaw: String? = null,
    @Column
    open var notes: String? = null,
    // Category
    @Column(name = "category_id")
    open var categoryId: UUID? = null,
    @Column(name = "category_source", length = 20)
    open var categorySource: String? = null,
    // Merchant
    @Column(name = "merchant_id")
    open var merchantId: UUID? = null,
    // Counterparty
    @Column(name = "counterparty_name")
    open var counterpartyName: String? = null,
    @Column(name = "counterparty_document")
    open var counterpartyDocument: String? = null,
    @Column(name = "counterparty_bank_code", length = 3)
    open var counterpartyBankCode: String? = null,
    @Column(name = "counterparty_account_number")
    open var counterpartyAccountNumber: String? = null,
    @Column(name = "payment_reason")
    open var paymentReason: String? = null,
    // Transfer
    @Column(name = "transfer_peer_transaction_id")
    open var transferPeerTransactionId: UUID? = null,
    // Credit card
    @Column(name = "bill_id")
    open var billId: UUID? = null,
    @Column(name = "installment_number")
    open var installmentNumber: Short? = null,
    @Column(name = "total_installments")
    open var totalInstallments: Short? = null,
    @Column(name = "payee_mcc")
    open var payeeMcc: Int? = null,
    @Column(name = "card_identifier")
    open var cardIdentifier: String? = null,
    @Column(name = "bill_post_date")
    open var billPostDate: LocalDate? = null,
    // OFB/Pluggy sync
    @Column(name = "provider_transaction_id")
    open var providerTransactionId: String? = null,
    @Column(name = "provider_account_id")
    open var providerAccountId: String? = null,
    // Import dedup (Level 2: cross-source fingerprint)
    @Column(name = "import_fingerprint")
    open var importFingerprint: String? = null,
    @Column(name = "import_job_id")
    open var importJobId: UUID? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_data", columnDefinition = "jsonb")
    open var sourceData: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applied_rule_ids", columnDefinition = "jsonb")
    open var appliedRuleIds: String? = null,
    // Soft delete + audit
    @Column(name = "deleted_at")
    open var deletedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
