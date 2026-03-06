package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.transaction.MonthTransactionTotals
import io.github.salomax.neotool.banking.domain.transaction.MonthTransferTotals
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionBelongsToOption
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionRepository
import io.github.salomax.neotool.banking.model.transaction.TransactionMapper
import io.github.salomax.neotool.banking.model.transaction.TransactionTagEntity
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Default implementation of [TransactionRepository] using JPA.
 */
@Singleton
open class DefaultTransactionRepository(
    private val entityRepository: TransactionEntityRepository,
    private val transactionTagEntityRepository: TransactionTagEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : TransactionRepository {
    override fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<Transaction> =
        withTags(
            entityRepository
                .findByFinancialAccountIdAndAccountIdAndDeletedAtIsNullOrderByDateDescAndCreatedAtDesc(
                    financialAccountId,
                    accountId,
                )
                .map(TransactionMapper::toDomain),
            accountId,
        )

    override fun findByBillId(
        billId: UUID,
        accountId: UUID,
    ): List<Transaction> =
        withTags(
            entityRepository
                .findByBillIdAndAccountIdAndDeletedAtIsNullOrderByDateDescAndCreatedAtDesc(
                    billId,
                    accountId,
                )
                .map(TransactionMapper::toDomain),
            accountId,
        )

    override fun findByAccount(accountId: UUID): List<Transaction> =
        withTags(
            entityRepository
                .findByAccountIdAndDeletedAtIsNullOrderByDateDescAndCreatedAtDesc(accountId)
                .map(TransactionMapper::toDomain),
            accountId,
        )

    override fun listBelongsToOptions(accountId: UUID): List<TransactionBelongsToOption> {
        val userOptions =
            entityRepository.findDistinctBelongsToUserIds(accountId)
                .map { TransactionBelongsToOption(userId = it) }
        val nameOptions =
            entityRepository.findDistinctBelongsToNames(accountId)
                .map { TransactionBelongsToOption(name = it) }
        return userOptions + nameOptions
    }

    override fun listTags(accountId: UUID): List<String> =
        transactionTagEntityRepository.findDistinctTagsByAccountId(accountId)

    override fun findById(
        id: UUID,
        accountId: UUID,
    ): Transaction? =
        entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(id, accountId)
            ?.let(TransactionMapper::toDomain)
            ?.let { withTags(listOf(it), accountId).first() }

    @Transactional
    override fun save(transaction: Transaction): Transaction {
        val entity = TransactionMapper.toEntity(transaction)
        val saved = entityRepository.save(entity)
        val savedId = requireNotNull(saved.id) { "Saved transaction id is required" }
        syncTags(savedId, transaction.accountId, transaction.tags)
        return withTags(listOf(TransactionMapper.toDomain(saved)), transaction.accountId).first()
    }

    @Transactional
    override fun update(transaction: Transaction): Transaction {
        val id = requireNotNull(transaction.id) { "Transaction id is required for update" }
        val entity =
            entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(id, transaction.accountId)
                ?: throw NoSuchElementException("Transaction $id not found")
        TransactionMapper.updateEntity(entity, transaction)
        val saved = entityRepository.update(entity)
        // Ensure bill/date changes are visible to subsequent native recomputes in the same transaction.
        entityManager.flush()
        syncTags(id, transaction.accountId, transaction.tags)
        return withTags(listOf(TransactionMapper.toDomain(saved)), transaction.accountId).first()
    }

    override fun softDelete(
        id: UUID,
        accountId: UUID,
    ): Boolean = entityRepository.softDelete(id, accountId, Instant.now()) > 0

    @Transactional(readOnly = true)
    override fun getMonthTotals(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ): MonthTransactionTotals {
        @Suppress("UNCHECKED_CAST")
        val cols =
            entityManager.createNativeQuery(MONTH_TOTALS_SQL)
                .setParameter("financialAccountId", financialAccountId.toString())
                .setParameter("year", year)
                .setParameter("month", month)
                .singleResult as Array<Any?>
        return MonthTransactionTotals(
            financialAccountId = financialAccountId,
            year = year,
            month = month,
            totalCredit = cols[0] as BigDecimal,
            totalDebit = cols[1] as BigDecimal,
            totalIncome = cols[2] as BigDecimal,
            totalExpenses = cols[3] as BigDecimal,
            totalFutureCredit = cols[4] as BigDecimal,
            totalFutureDebit = cols[5] as BigDecimal,
        )
    }

    @Transactional(readOnly = true)
    override fun getMonthTransferTotals(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ): MonthTransferTotals {
        @Suppress("UNCHECKED_CAST")
        val cols =
            entityManager.createNativeQuery(MONTH_TRANSFER_TOTALS_SQL)
                .setParameter("financialAccountId", financialAccountId.toString())
                .setParameter("year", year)
                .setParameter("month", month)
                .singleResult as Array<Any?>
        return MonthTransferTotals(
            transfersIn = cols[0] as BigDecimal,
            transfersOut = cols[1] as BigDecimal,
        )
    }

    override fun findFinancialAccountIdsByAccount(accountId: UUID): List<UUID> =
        entityRepository.findFinancialAccountIdsByAccount(accountId)

    override fun findInitialBalanceDate(financialAccountId: UUID): LocalDate? =
        entityRepository.findEarliestBalanceAdjustmentDate(financialAccountId.toString())

    companion object {
        private val MONTH_TOTALS_SQL =
            """
            SELECT
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT' AND status IN ('POSTED','PENDING')), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'  AND status IN ('POSTED','PENDING')), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT' AND status IN ('POSTED','PENDING')
                                            AND type NOT IN ('TRANSFER','BALANCE_ADJUSTMENT')), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'  AND status IN ('POSTED','PENDING')
                                            AND type NOT IN ('TRANSFER','BALANCE_ADJUSTMENT')), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT' AND status = 'FUTURE'), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'  AND status = 'FUTURE'), 0)
            FROM banking.transactions
            WHERE financial_account_id = CAST(:financialAccountId AS uuid)
              AND EXTRACT(YEAR  FROM date) = :year
              AND EXTRACT(MONTH FROM date) = :month
              AND deleted_at IS NULL
            """.trimIndent()

        private val MONTH_TRANSFER_TOTALS_SQL =
            """
            SELECT
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT'), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'),  0)
            FROM banking.transactions
            WHERE financial_account_id = CAST(:financialAccountId AS uuid)
              AND type = 'TRANSFER'
              AND status IN ('POSTED','PENDING')
              AND EXTRACT(YEAR  FROM date) = :year
              AND EXTRACT(MONTH FROM date) = :month
              AND deleted_at IS NULL
            """.trimIndent()
    }

    private fun withTags(
        transactions: List<Transaction>,
        accountId: UUID,
    ): List<Transaction> {
        if (transactions.isEmpty()) return transactions
        val ids = transactions.mapNotNull { it.id }
        if (ids.isEmpty()) return transactions

        val tagsByTransactionId =
            transactionTagEntityRepository
                .findByTransactionIdInAndAccountId(ids, accountId)
                .groupBy { it.transactionId }
                .mapValues { (_, rows) -> rows.map { it.tag } }

        return transactions.map { tx ->
            tx.copy(tags = tagsByTransactionId[tx.id].orEmpty())
        }
    }

    private fun syncTags(
        transactionId: UUID,
        accountId: UUID,
        tags: List<String>,
    ) {
        transactionTagEntityRepository.deleteByTransactionIdAndAccountId(transactionId, accountId)
        if (tags.isEmpty()) return
        val now = Instant.now()
        val entities =
            tags.map { tag ->
                TransactionTagEntity(
                    transactionId = transactionId,
                    tag = tag,
                    accountId = accountId,
                    createdAt = now,
                )
            }
        transactionTagEntityRepository.saveAll(entities)
    }
}

/**
 * Default implementation of [TransactionCategoryRepository] using JPA.
 */
@Singleton
open class DefaultTransactionCategoryRepository(
    private val entityRepository: TransactionCategoryEntityRepository,
) : TransactionCategoryRepository {
    override fun findAll(accountId: UUID): List<TransactionCategory> =
        entityRepository.findByAccountIdIsNullOrAccountIdAndDeletedAtIsNull(accountId)
            .map(TransactionMapper::toDomain)

    override fun findById(id: UUID): TransactionCategory? =
        entityRepository.findByIdAndDeletedAtIsNull(id)
            ?.let(TransactionMapper::toDomain)

    @Transactional
    override fun save(category: TransactionCategory): TransactionCategory {
        val entity = TransactionMapper.toEntity(category)
        val saved = entityRepository.save(entity)
        return TransactionMapper.toDomain(saved)
    }
}
