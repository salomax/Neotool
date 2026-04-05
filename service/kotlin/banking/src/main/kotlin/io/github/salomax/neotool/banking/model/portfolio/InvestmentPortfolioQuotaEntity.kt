package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
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
@Table(name = "investment_portfolio_quota", schema = "banking")
open class InvestmentPortfolioQuotaEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "financial_account_id", nullable = false)
    open var financialAccountId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "dimension_type", nullable = false, length = 20)
    open var dimensionType: PortfolioDimensionType,
    @Column(name = "dimension_key", nullable = false, length = 50)
    open var dimensionKey: String = "",
    @Column(name = "quota_date", nullable = false)
    open var quotaDate: LocalDate,
    @Column(name = "quota_value", nullable = false, precision = 18, scale = 8)
    open var quotaValue: BigDecimal,
    @Column(name = "total_quotas", nullable = false, precision = 18, scale = 8)
    open var totalQuotas: BigDecimal,
    @Column(name = "total_portfolio_value", nullable = false, precision = 18, scale = 2)
    open var totalPortfolioValue: BigDecimal,
    @Column(name = "has_estimated_values", nullable = false)
    open var hasEstimatedValues: Boolean = false,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
