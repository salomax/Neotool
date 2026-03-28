package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import jakarta.persistence.PrimaryKeyJoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "investment_transactions", schema = "banking")
open class InvestmentTransactionEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,

    // Ownership
    @Column(name = "holding_id", nullable = false)
    open var holdingId: UUID,

    @Column(name = "financial_account_id", nullable = false)
    open var financialAccountId: UUID,

    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,

    @Column(name = "created_by_user_id", nullable = false)
    open var createdByUserId: UUID,

    // Classification
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    open var direction: InvestmentTransactionDirection,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    open var type: InvestmentTransactionType,

    @Column(name = "type_additional_info")
    open var typeAdditionalInfo: String? = null,

    // Amounts
    @Column(precision = 18, scale = 8)
    open var quantity: BigDecimal? = null,

    @Column(name = "unit_price", precision = 18, scale = 8)
    open var unitPrice: BigDecimal? = null,

    @Column(name = "gross_value", nullable = false, precision = 18, scale = 4)
    open var grossValue: BigDecimal,

    @Column(name = "net_value", precision = 18, scale = 4)
    open var netValue: BigDecimal? = null,

    // Brazilian taxes
    @Column(name = "income_tax", precision = 18, scale = 4)
    open var incomeTax: BigDecimal? = null,

    @Column(precision = 18, scale = 4)
    open var iof: BigDecimal? = null,

    // Fixed income context
    @Column(name = "indexer_percentage", precision = 8, scale = 6)
    open var indexerPercentage: BigDecimal? = null,

    @Column(name = "agreed_rate", precision = 10, scale = 8)
    open var fixedAnnualRate: BigDecimal? = null,

    // Equity context
    @Column(name = "price_factor", precision = 10, scale = 6)
    open var priceFactor: BigDecimal? = null,

    // Dates
    @Column(name = "trade_date", nullable = false)
    open var tradeDate: LocalDate,

    @Column(name = "settlement_date")
    open var settlementDate: LocalDate? = null,

    // Provenance
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var source: InvestmentSource = InvestmentSource.MANUAL,

    @Column(name = "provider_transaction_id")
    open var providerTransactionId: String? = null,

    @Column(name = "import_job_id")
    open var importJobId: UUID? = null,

    @Column(name = "deleted_at")
    open var deletedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),

    // 1:1 broker note (renda variável only)
    @OneToOne(mappedBy = "investmentTransaction", cascade = [CascadeType.ALL], optional = true)
    open var brokerNote: BrokerNoteEntity? = null,
) : BaseEntity<UUID?>(id)
