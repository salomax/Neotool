package io.github.salomax.neotool.banking.service.account

import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class FinancialAccountGuard(
    private val financialAccountRepository: FinancialAccountRepository,
) {
    fun requireOwnership(
        financialAccountId: UUID,
        accountId: UUID,
    ) {
        require(financialAccountRepository.existsByIdAndAccountId(financialAccountId, accountId)) {
            "Financial account $financialAccountId does not belong to account $accountId"
        }
    }

    fun requireOwnershipIfPresent(
        financialAccountId: UUID?,
        accountId: UUID,
    ) {
        if (financialAccountId != null) requireOwnership(financialAccountId, accountId)
    }
}
