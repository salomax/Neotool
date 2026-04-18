package io.github.salomax.neotool.banking.repo.account

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.model.account.FinancialAccountMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
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
        private val CARD_MATCHABLE_SUBTYPES =
            setOf(
                FinancialAccountSubtype.CREDIT_CARD,
                FinancialAccountSubtype.CHARGE_CARD,
            )
    }

    override fun findByAccountId(accountId: UUID): List<FinancialAccount> =
        entityRepository.findByAccountIdAndDeletedAtIsNull(accountId)
            .map(FinancialAccountMapper::toDomain)

    override fun findById(
        id: UUID,
        accountId: UUID,
    ): FinancialAccount? =
        entityRepository
            .findByIdAndAccountIdAndDeletedAtIsNull(id, accountId)
            ?.let(FinancialAccountMapper::toDomain)

    override fun existsByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): Boolean = entityRepository.existsByIdAndAccountIdAndDeletedAtIsNull(id, accountId)

    override fun findAllInvestmentAccounts(): List<FinancialAccount> =
        entityRepository.findByTypeAndDeletedAtIsNull(FinancialAccountType.INVESTMENT)
            .map(FinancialAccountMapper::toDomain)

    override fun findCardAccountsByLastFourDigits(
        accountId: UUID,
        lastFourDigits: String,
    ): List<FinancialAccount> =
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

    @Transactional
    override fun update(account: FinancialAccount): FinancialAccount {
        val id = requireNotNull(account.id) { "Account id is required for update" }
        val existing =
            requireNotNull(entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(id, account.accountId)) {
                "FinancialAccount not found: $id"
            }

        FinancialAccountMapper.updateEntity(existing, account)

        account.bankData?.let { data ->
            val current = existing.bankData
            if (current == null) {
                val bankDataEntity = FinancialAccountMapper.toBankDataEntity(data, existing)
                val savedBankData = bankDataRepository.save(bankDataEntity)
                existing.bankData = savedBankData
            } else {
                current.branchCode = data.branchCode
                current.number = data.number
                current.checkDigit = data.checkDigit
                current.maskedNumber = data.maskedNumber
                bankDataRepository.update(current)
            }
        }

        account.cardData?.let { data ->
            val current = existing.cardData
            if (current == null) {
                val cardDataEntity = FinancialAccountMapper.toCardDataEntity(data, existing)
                val savedCardData = cardDataRepository.save(cardDataEntity)
                existing.cardData = savedCardData
            } else {
                current.maskedNumber = data.maskedNumber
                current.bin = data.bin
                current.cardNetwork = data.cardNetwork
                current.cardLevel = data.cardLevel
                current.creditLimit = data.creditLimit
                current.availableCreditLimit = data.availableCreditLimit
                current.minPaymentPercentage = data.minimumPaymentPercentage
                current.closingDay = data.closingDay
                current.dueDay = data.dueDay
                current.linkedBankAccountId = data.linkedBankAccountId
                current.isMultiCard = data.isMultiCard
                cardDataRepository.update(current)
            }
        }

        entityRepository.update(existing)
        return FinancialAccountMapper.toDomain(existing)
    }

    @Transactional
    override fun softDelete(
        id: UUID,
        accountId: UUID,
    ): Boolean {
        val existing = entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(id, accountId) ?: return false
        val now = Instant.now()
        existing.deletedAt = now
        existing.updatedAt = now
        entityRepository.update(existing)
        return true
    }
}
