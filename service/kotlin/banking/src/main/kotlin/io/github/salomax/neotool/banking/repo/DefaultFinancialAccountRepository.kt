package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.model.account.FinancialAccountMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Default implementation of [FinancialAccountRepository] using JPA.
 *
 * CTI save flow: persist base entity first (gets DB-generated UUID via @Generated),
 * then persist bank/card data referencing the saved entity's id.
 */
@Singleton
open class DefaultFinancialAccountRepository(
    private val entityRepository: FinancialAccountEntityRepository,
    private val bankDataRepository: FinancialAccountBankDataEntityRepository,
    private val cardDataRepository: FinancialAccountCardDataEntityRepository,
) : FinancialAccountRepository {
    companion object {
        private val CARD_MATCHABLE_SUBTYPES = setOf(
            FinancialAccountSubtype.CREDIT_CARD,
            FinancialAccountSubtype.CHARGE_CARD,
        )
    }

    override fun findByAccountId(accountId: UUID): List<FinancialAccount> =
        entityRepository.findByAccountIdAndDeletedAtIsNull(accountId)
            .map(FinancialAccountMapper::toDomain)

    override fun findCardAccountsByLastFourDigits(accountId: UUID, lastFourDigits: String): List<FinancialAccount> =
        entityRepository.findByAccountIdAndSubtypeInAndCardLastFourDigitsAndDeletedAtIsNull(
            accountId = accountId,
            subtypes = CARD_MATCHABLE_SUBTYPES,
            cardLastFourDigits = lastFourDigits,
        ).map(FinancialAccountMapper::toDomain)

    @Transactional
    override fun save(account: FinancialAccount): FinancialAccount {
        val entity = FinancialAccountMapper.toEntity(account)
        val saved = entityRepository.save(entity)

        account.bankData?.let { data ->
            val bankDataEntity = FinancialAccountMapper.toBankDataEntity(data, saved)
            val savedBankData = bankDataRepository.save(bankDataEntity)
            saved.bankData = savedBankData
        }
        account.cardData?.let { data ->
            val cardDataEntity = FinancialAccountMapper.toCardDataEntity(data, saved)
            val savedCardData = cardDataRepository.save(cardDataEntity)
            saved.cardData = savedCardData
        }

        return FinancialAccountMapper.toDomain(saved)
    }
}
