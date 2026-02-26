package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.model.account.FinancialAccountMapper
import jakarta.inject.Singleton

/**
 * Default implementation of [FinancialAccountRepository] using JPA and [FinancialAccountMapper].
 * Methods will be added as development progresses.
 */
@Singleton
open class DefaultFinancialAccountRepository(
    private val entityRepository: FinancialAccountEntityRepository,
    private val bankDataRepository: FinancialAccountBankDataEntityRepository,
    private val cardDataRepository: FinancialAccountCardDataEntityRepository,
    private val mapper: FinancialAccountMapper,
) : FinancialAccountRepository
