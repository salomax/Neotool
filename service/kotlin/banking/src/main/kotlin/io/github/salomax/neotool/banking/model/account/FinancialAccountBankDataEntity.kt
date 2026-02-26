package io.github.salomax.neotool.banking.model.account

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA entity for banking.financial_account_bank_data (CTI extension for type = BANK).
 */
@Entity
@Table(name = "financial_account_bank_data", schema = "banking")
open class FinancialAccountBankDataEntity(
    @Id
    @Column(name = "account_id")
    val accountId: UUID,
    @OneToOne
    @MapsId
    @JoinColumn(name = "account_id")
    val account: FinancialAccountEntity,
    @Column(name = "branch_code", length = 10)
    open var branchCode: String? = null,
    @Column(name = "number", length = 20)
    open var number: String? = null,
    @Column(name = "check_digit", length = 2)
    open var checkDigit: String? = null,
    @Column(name = "masked_number", length = 20)
    open var maskedNumber: String? = null,
)
