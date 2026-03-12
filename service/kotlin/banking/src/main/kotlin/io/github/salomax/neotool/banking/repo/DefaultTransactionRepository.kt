package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategoryBreakdownRow
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.MonthTransactionTotals
import io.github.salomax.neotool.banking.domain.transaction.MonthTransferTotals
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionBelongsToOption
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
            totalFutureIncome = cols[6] as BigDecimal,
            totalFutureExpenses = cols[7] as BigDecimal,
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

    override fun findInDateRangeByCategoryIds(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
        categoryIds: List<UUID>,
    ): List<Transaction> =
        withTags(
            entityRepository
                .findInDateRangeByCategoryIds(
                    accountId = accountId,
                    financialAccountId = financialAccountId,
                    year = year,
                    month = month,
                    categoryIds = categoryIds,
                )
                .map(TransactionMapper::toDomain),
            accountId,
        )

    @Transactional(readOnly = true)
    override fun getCategoryBreakdown(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryBreakdownRow> {
        val query =
            entityManager.createNativeQuery(CATEGORY_BREAKDOWN_SQL)
                .setParameter("accountId", accountId)
                .setParameter("financialAccountId", financialAccountId)
                .setParameter("year", year)
                .setParameter("month", month)
                .setParameter("applicableTo", applicableTo?.name)

        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>
        return rows.map { row ->
            CategoryBreakdownRow(
                categoryId = row[0] as UUID,
                categoryName = row[1] as String,
                categoryIcon = row[2] as? String,
                categoryColor = row[3] as? String,
                parentId = row[4] as? UUID,
                applicableTo = CategoryApplicableTo.valueOf(row[5] as String),
                isSystem = (row[6] as Boolean),
                sortOrder = (row[7] as Number).toInt(),
                totalAmount = row[8] as BigDecimal,
                transactionCount = (row[9] as Number).toLong(),
            )
        }
    }

    override fun findInDateRangeUncategorized(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
    ): List<Transaction> =
        withTags(
            entityRepository
                .findInDateRangeUncategorized(
                    accountId = accountId,
                    financialAccountId = financialAccountId,
                    year = year,
                    month = month,
                )
                .map(TransactionMapper::toDomain),
            accountId,
        )

    @Transactional
    override fun bulkUpdateCategory(
        transactionIds: List<UUID>,
        categoryId: UUID,
        categorySource: CategorySource,
        accountId: UUID,
    ): Int {
        if (transactionIds.isEmpty()) return 0
        val placeholders = transactionIds.indices.joinToString(",") { ":id$it" }
        val query =
            entityManager.createNativeQuery(
                """
                UPDATE banking.transactions
                   SET category_id = CAST(:categoryId AS uuid),
                       category_source = :categorySource,
                       updated_at = NOW()
                 WHERE account_id = CAST(:accountId AS uuid)
                   AND deleted_at IS NULL
                   AND id IN ($placeholders)
                """.trimIndent(),
            )
                .setParameter("categoryId", categoryId.toString())
                .setParameter("categorySource", categorySource.name)
                .setParameter("accountId", accountId.toString())
        transactionIds.forEachIndexed { i, uuid ->
            query.setParameter("id$i", uuid)
        }
        return query.executeUpdate()
    }

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
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'  AND status = 'FUTURE'), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT' AND status = 'FUTURE'
                                            AND type NOT IN ('TRANSFER','BALANCE_ADJUSTMENT')), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'  AND status = 'FUTURE'
                                            AND type NOT IN ('TRANSFER','BALANCE_ADJUSTMENT')), 0)
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

        private val CATEGORY_BREAKDOWN_SQL =
            """
            SELECT
              tc.id             AS category_id,
              tc.name           AS category_name,
              tc.icon           AS category_icon,
              tc.color          AS category_color,
              tc.parent_id      AS parent_id,
              tc.applicable_to  AS applicable_to,
              tc.is_system      AS is_system,
              tc.sort_order     AS sort_order,
              SUM(t.amount)     AS total_amount,
              COUNT(t.id)       AS transaction_count
            FROM banking.transactions t
            JOIN banking.transaction_categories tc ON t.category_id = tc.id AND tc.deleted_at IS NULL
            WHERE t.account_id = CAST(:accountId AS uuid)
              AND (CAST(:financialAccountId AS uuid) IS NULL OR t.financial_account_id = CAST(:financialAccountId AS uuid))
              AND EXTRACT(YEAR  FROM t.date) = :year
              AND EXTRACT(MONTH FROM t.date) = :month
              AND t.deleted_at IS NULL
              AND t.status IN ('POSTED', 'PENDING')
              AND (CAST(:applicableTo AS text) IS NULL
                   OR tc.applicable_to = :applicableTo
                   OR tc.applicable_to = 'ANY')
              AND (CAST(:applicableTo AS text) IS NULL
                   OR t.credit_debit_type = CASE :applicableTo
                       WHEN 'INCOME' THEN 'CREDIT'
                       WHEN 'EXPENSE' THEN 'DEBIT'
                       ELSE t.credit_debit_type END)
            GROUP BY tc.id, tc.name, tc.icon, tc.color, tc.parent_id, tc.applicable_to, tc.is_system, tc.sort_order
            ORDER BY total_amount DESC
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
