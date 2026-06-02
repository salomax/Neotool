package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Broker note fee breakdown. 1:1 with [InvestmentTransactionEntity].
 * Shares the same PK via @MapsId — same UUID as the parent transaction.
 */
@Entity
@Table(name = "broker_notes", schema = "banking")
open class BrokerNoteEntity(
    @Id
    @Column(name = "investment_transaction_id", columnDefinition = "uuid")
    override val id: UUID? = null,
    @MapsId
    @OneToOne
    @JoinColumn(name = "investment_transaction_id")
    open var investmentTransaction: InvestmentTransactionEntity? = null,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "broker_note_number")
    open var brokerNoteNumber: String? = null,
    @Column(name = "gross_value", nullable = false, precision = 18, scale = 4)
    open var grossValue: BigDecimal,
    @Column(name = "brokerage_fee", nullable = false, precision = 18, scale = 4)
    open var brokerageFee: BigDecimal = BigDecimal.ZERO,
    @Column(name = "clearing_settlement_fee", nullable = false, precision = 18, scale = 4)
    open var clearingSettlementFee: BigDecimal = BigDecimal.ZERO,
    @Column(name = "clearing_registration_fee", nullable = false, precision = 18, scale = 4)
    open var clearingRegistrationFee: BigDecimal = BigDecimal.ZERO,
    @Column(name = "stock_exchange_asset_trade_notice_fee", nullable = false, precision = 18, scale = 4)
    open var stockExchangeAssetTradeNoticeFee: BigDecimal = BigDecimal.ZERO,
    @Column(name = "stock_exchange_fee", nullable = false, precision = 18, scale = 4)
    open var stockExchangeFee: BigDecimal = BigDecimal.ZERO,
    @Column(name = "clearing_custody_fee", nullable = false, precision = 18, scale = 4)
    open var clearingCustodyFee: BigDecimal = BigDecimal.ZERO,
    @Column(name = "income_tax", nullable = false, precision = 18, scale = 4)
    open var incomeTax: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 18, scale = 4)
    open var taxes: BigDecimal = BigDecimal.ZERO,
    @Column(name = "net_value", nullable = false, precision = 18, scale = 4)
    open var netValue: BigDecimal,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
