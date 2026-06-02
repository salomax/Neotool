package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
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
@Table(name = "investment_portfolio_flow_event", schema = "banking")
open class InvestmentPortfolioFlowEventEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "financial_account_id", nullable = false)
    open var financialAccountId: UUID,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "event_date", nullable = false)
    open var eventDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    open var eventType: PortfolioFlowEventType,
    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 2)
    open var grossAmount: BigDecimal,
    @Column(name = "portfolio_value_before", nullable = false, precision = 18, scale = 2)
    open var portfolioValueBefore: BigDecimal,
    @Column(name = "quota_value_at_event", nullable = false, precision = 18, scale = 8)
    open var quotaValueAtEvent: BigDecimal,
    @Column(name = "quotas_transacted", nullable = false, precision = 18, scale = 8)
    open var quotasTransacted: BigDecimal,
    @Column(name = "related_holding_id")
    open var relatedHoldingId: UUID? = null,
    @Column(name = "source_reference_id")
    open var sourceReferenceId: UUID? = null,
    @Column(name = "source_reference_type", length = 50)
    open var sourceReferenceType: String? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
