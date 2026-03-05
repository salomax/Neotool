package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.model.account.FinancialAccountEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for banking.financial_accounts (entity layer).
 */
@Repository
interface FinancialAccountEntityRepository : JpaRepository<FinancialAccountEntity, UUID> {
    fun findByAccountIdAndDeletedAtIsNull(accountId: UUID): List<FinancialAccountEntity>
    fun findByAccountIdAndSubtypeInAndCardLastFourDigitsAndDeletedAtIsNull(
        accountId: UUID,
        subtypes: Collection<FinancialAccountSubtype>,
        cardLastFourDigits: String,
    ): List<FinancialAccountEntity>
}
