package io.github.salomax.neotool.banking.model.account

import io.github.salomax.neotool.banking.domain.account.CreditCardLevel
import io.github.salomax.neotool.banking.domain.account.CreditCardNetwork
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

/**
 * JPA entity for banking.financial_account_card_data (CTI extension for type = CREDIT or PAYMENT).
 * Credit-only fields (credit_limit, closing_day, due_day) are nullable; NOT NULL for credit cards at service layer.
 */
@Entity
@Table(name = "financial_account_card_data", schema = "banking")
open class FinancialAccountCardDataEntity(
    @Id
    @Column(name = "financial_account_id")
    open var financialAccountId: UUID,
    @OneToOne
    @MapsId
    @JoinColumn(name = "financial_account_id")
    open var account: FinancialAccountEntity,
    @Column(name = "masked_number", length = 10)
    open var maskedNumber: String? = null,
    @Column(name = "bin", length = 8)
    open var bin: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "card_network", length = 30)
    open var cardNetwork: CreditCardNetwork? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "card_level", length = 20)
    open var cardLevel: CreditCardLevel? = null,
    @Column(name = "credit_limit", precision = 18, scale = 2)
    open var creditLimit: BigDecimal? = null,
    @Column(name = "available_credit_limit", precision = 18, scale = 2)
    open var availableCreditLimit: BigDecimal? = null,
    @Column(name = "min_payment_percentage", precision = 5, scale = 2)
    open var minPaymentPercentage: BigDecimal? = null,
    @Column(name = "closing_day")
    open var closingDay: Int? = null,
    @Column(name = "due_day")
    open var dueDay: Int? = null,
    @Column(name = "linked_bank_account_id")
    open var linkedBankAccountId: UUID? = null,
)
