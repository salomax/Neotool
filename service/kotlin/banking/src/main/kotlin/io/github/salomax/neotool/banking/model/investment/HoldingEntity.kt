package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "investment_holdings", schema = "banking")
open class HoldingEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,

    @Column(name = "financial_account_id", nullable = false)
    open var financialAccountId: UUID,

    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,

    @Column(name = "asset_id", nullable = false)
    open var assetId: UUID,

    @Column(nullable = false, precision = 18, scale = 8)
    open var quantity: BigDecimal,

    @Column(name = "average_cost", nullable = false, precision = 18, scale = 8)
    open var averageCost: BigDecimal = BigDecimal.ZERO,

    // ── Fixed income position-specific terms ──────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    open var indexer: Indexer? = null,

    @Column(name = "indexer_percentage", precision = 8, scale = 6)
    open var indexerPercentage: BigDecimal? = null,

    @Column(name = "pre_fixed_rate", precision = 10, scale = 8)
    open var preFixedRate: BigDecimal? = null,

    @Column(name = "issue_date")
    open var issueDate: LocalDate? = null,

    @Column(name = "maturity_date")
    open var maturityDate: LocalDate? = null,

    @Column(name = "grace_period_date")
    open var gracePeriodDate: LocalDate? = null,

    @Column(name = "is_tax_exempt", nullable = false)
    open var isTaxExempt: Boolean = false,

    // ── Provider-cached valuations ────────────────────────────────────────────
    @Column(name = "gross_value", precision = 18, scale = 4)
    open var grossValue: BigDecimal? = null,

    @Column(name = "net_value", precision = 18, scale = 4)
    open var netValue: BigDecimal? = null,

    @Column(name = "income_tax", precision = 18, scale = 4)
    open var incomeTax: BigDecimal? = null,

    @Column(precision = 18, scale = 4)
    open var iof: BigDecimal? = null,

    @Column(name = "last_valued_at")
    open var lastValuedAt: Instant? = null,

    // ── Provenance ────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var source: InvestmentSource = InvestmentSource.MANUAL,

    @Column(name = "provider_investment_id")
    open var providerInvestmentId: String? = null,

    @Column(name = "deleted_at")
    open var deletedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
