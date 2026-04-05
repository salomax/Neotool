package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "investment_portfolio_recalculation_job", schema = "banking")
open class InvestmentPortfolioRecalculationJobEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "financial_account_id")
    open var financialAccountId: UUID? = null,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var status: PortfolioRecalculationStatus,
    @Column(name = "recalculate_from", nullable = false)
    open var recalculateFrom: LocalDate,
    @Column(name = "triggered_by", nullable = false, length = 100)
    open var triggeredBy: String,
    @Column(name = "days_processed")
    open var daysProcessed: Int? = null,
    @Column(name = "error_details")
    open var errorDetails: String? = null,
    @Column(name = "started_at")
    open var startedAt: Instant? = null,
    @Column(name = "completed_at")
    open var completedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
