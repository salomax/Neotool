package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategoryBreakdownRow
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.MonthTransactionTotals
import io.github.salomax.neotool.banking.domain.transaction.MonthTransferTotals
import io.github.salomax.neotool.banking.domain.transaction.TagBreakdownRow
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionBelongsToOption
import io.github.salomax.neotool.banking.repo.transaction.TransactionListQuery
import io.github.salomax.neotool.banking.repo.transaction.TransactionPageResult
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.model.transaction.TransactionEntity
import io.github.salomax.neotool.banking.model.transaction.TransactionMapper
import io.github.salomax.neotool.banking.model.transaction.TransactionTagEntity
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.Query
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.slf4j.LoggerFactory

/**
 * Default implementation of [TransactionRepository] using JPA.
 */
@Singleton
open class DefaultTransactionRepository(
    private val entityRepository: TransactionEntityRepository,
    private val transactionTagEntityRepository: TransactionTagEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : TransactionRepository {
    private val logger = LoggerFactory.getLogger(DefaultTransactionRepository::class.java)

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

    override fun findByTransferPeerTransactionId(
        transferPeerTransactionId: UUID,
        accountId: UUID,
    ): Transaction? =
        entityRepository
            .findByTransferPeerTransactionIdAndAccountIdAndDeletedAtIsNull(transferPeerTransactionId, accountId)
            ?.let(TransactionMapper::toDomain)
            ?.let { withTags(listOf(it), accountId).first() }

    override fun findByTransferPeerTransactionIds(
        transferPeerTransactionIds: Collection<UUID>,
        accountId: UUID,
    ): List<Transaction> {
        if (transferPeerTransactionIds.isEmpty()) return emptyList()
        return entityRepository
            .findByTransferPeerTransactionIdInAndAccountIdAndDeletedAtIsNull(
                transferPeerTransactionIds.distinct().toList(),
                accountId,
            )
            .map(TransactionMapper::toDomain)
    }

    @Transactional
    override fun save(transaction: Transaction): Transaction {
        val entity = TransactionMapper.toEntity(transaction)
        val saved = entityRepository.save(entity)
        // Ensure newly inserted rows are visible to subsequent native recomputes in the same transaction.
        entityManager.flush()
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
            totalCreditExclTransfer = cols[4] as BigDecimal,
            totalDebitExclTransfer = cols[5] as BigDecimal,
            totalFutureCredit = cols[6] as BigDecimal,
            totalFutureDebit = cols[7] as BigDecimal,
            totalFutureIncome = cols[8] as BigDecimal,
            totalFutureExpenses = cols[9] as BigDecimal,
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

    override fun findEarliestTransactionDate(financialAccountId: UUID): LocalDate? =
        entityRepository.findEarliestTransactionDate(financialAccountId.toString())

    override fun findEarliestTransactionDates(financialAccountIds: Collection<UUID>): Map<UUID, LocalDate> {
        if (financialAccountIds.isEmpty()) return emptyMap()
        val ids = financialAccountIds.toList()
        val placeholders = ids.indices.joinToString(", ") { "CAST(:p$it AS uuid)" }
        val sql = """
            SELECT financial_account_id, MIN(date)
              FROM banking.transactions
             WHERE financial_account_id IN ($placeholders)
               AND deleted_at IS NULL
             GROUP BY financial_account_id
        """.trimIndent()
        val query = entityManager.createNativeQuery(sql)
        ids.forEachIndexed { i, id -> query.setParameter("p$i", id.toString()) }
        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>
        return rows.associate { row ->
            val financialAccountId = when (val raw = row[0]) {
                is UUID -> raw
                else -> UUID.fromString(raw.toString())
            }
            val earliestDate = when (val raw = row[1]) {
                is LocalDate -> raw
                is java.sql.Date -> raw.toLocalDate()
                else -> LocalDate.parse(raw.toString())
            }
            financialAccountId to earliestDate
        }
    }

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

    override fun findInDateRange(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
    ): List<Transaction> =
        withTags(
            entityRepository
                .findInDateRange(
                    accountId = accountId,
                    financialAccountId = financialAccountId,
                    year = year,
                    month = month,
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
            TransactionListQuery(
                accountId = accountId,
                financialAccountId = financialAccountId,
                includedFinancialAccountIds = null,
                belongsToUserId = null,
                belongsToName = null,
                search = null,
                tags = null,
                billId = null,
                categoryIds = null,
                uncategorizedOnly = false,
                creditDebitType =
                    when (applicableTo) {
                        CategoryApplicableTo.INCOME -> io.github.salomax.neotool.banking.domain.transaction.CreditDebitType.CREDIT
                        CategoryApplicableTo.EXPENSE -> io.github.salomax.neotool.banking.domain.transaction.CreditDebitType.DEBIT
                        else -> null
                    },
                types = null,
                startDate = LocalDate.of(year, month, 1),
                endDateExclusive = LocalDate.of(year, month, 1).plusMonths(1),
                limit = 1,
                offset = 0,
            )

        return getFilteredCategoryBreakdown(query, applicableTo)
    }

    @Transactional(readOnly = true)
    override fun getCategoryBreakdownForAccounts(
        accountId: UUID,
        includedFinancialAccountIds: List<UUID>,
        year: Int,
        month: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryBreakdownRow> {
        if (includedFinancialAccountIds.isEmpty()) return emptyList()
        val query =
            TransactionListQuery(
                accountId = accountId,
                financialAccountId = null,
                includedFinancialAccountIds = includedFinancialAccountIds,
                belongsToUserId = null,
                belongsToName = null,
                search = null,
                tags = null,
                billId = null,
                categoryIds = null,
                uncategorizedOnly = false,
                creditDebitType =
                    when (applicableTo) {
                        CategoryApplicableTo.INCOME -> io.github.salomax.neotool.banking.domain.transaction.CreditDebitType.CREDIT
                        CategoryApplicableTo.EXPENSE -> io.github.salomax.neotool.banking.domain.transaction.CreditDebitType.DEBIT
                        else -> null
                    },
                types = null,
                startDate = LocalDate.of(year, month, 1),
                endDateExclusive = LocalDate.of(year, month, 1).plusMonths(1),
                limit = 1,
                offset = 0,
            )

        return getFilteredCategoryBreakdown(query, applicableTo)
    }

    @Transactional(readOnly = true)
    override fun getCategoryBreakdownForRange(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryBreakdownRow> {
        val startDate = LocalDate.of(startYear, startMonth, 1)
        val endDateExclusive = LocalDate.of(endYear, endMonth, 1).plusMonths(1)
        val query =
            TransactionListQuery(
                accountId = accountId,
                financialAccountId = financialAccountId,
                includedFinancialAccountIds = null,
                belongsToUserId = null,
                belongsToName = null,
                search = null,
                tags = null,
                billId = null,
                categoryIds = null,
                uncategorizedOnly = false,
                creditDebitType =
                    when (applicableTo) {
                        CategoryApplicableTo.INCOME -> io.github.salomax.neotool.banking.domain.transaction.CreditDebitType.CREDIT
                        CategoryApplicableTo.EXPENSE -> io.github.salomax.neotool.banking.domain.transaction.CreditDebitType.DEBIT
                        else -> null
                    },
                types = null,
                startDate = startDate,
                endDateExclusive = endDateExclusive,
                limit = 1,
                offset = 0,
            )

        return getFilteredCategoryBreakdown(query, applicableTo)
    }

    @Transactional(readOnly = true)
    override fun getFilteredCategoryBreakdown(
        query: TransactionListQuery,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryBreakdownRow> {
        val base = buildTransactionsBaseSql(query)
        val params = LinkedHashMap(base.params)
        params["applicableTo"] = applicableTo?.name
        val applicableToWhereClause =
            if (applicableTo != null) {
                """
                AND (
                    (ft.category_id IS NULL)
                    OR tc.applicable_to = :applicableTo
                    OR tc.applicable_to = 'ANY'
                )
                """.trimIndent()
            } else {
                ""
            }
        @Suppress("UNCHECKED_CAST")
        val rows =
            applyParameters(
                entityManager.createNativeQuery(
                    """
                    WITH filtered_transactions AS (
                    ${base.sql}
                    )
                    SELECT
                      CASE WHEN (ft.category_id IS NULL) THEN NULL ELSE tc.id END   AS category_id,
                      CASE WHEN (ft.category_id IS NULL) THEN NULL ELSE tc.name END AS category_name,
                      CASE WHEN (ft.category_id IS NULL) THEN NULL ELSE tc.icon END AS category_icon,
                      CASE
                        WHEN (ft.category_id IS NULL) THEN NULL
                        ELSE tc.color
                      END               AS category_color,
                      CASE
                        WHEN (ft.category_id IS NULL) THEN NULL
                        ELSE tc.parent_id
                      END               AS parent_id,
                      CASE
                        WHEN (ft.category_id IS NULL) THEN COALESCE(CAST(:applicableTo AS text), 'ANY')
                        ELSE tc.applicable_to
                      END               AS applicable_to,
                      CASE
                        WHEN (ft.category_id IS NULL) THEN TRUE
                        ELSE tc.is_system
                      END               AS is_system,
                      CASE
                        WHEN (ft.category_id IS NULL) THEN :uncategorizedSortOrder
                        ELSE tc.sort_order
                      END               AS sort_order,
                      (ft.category_id IS NULL) AS is_uncategorized,
                      SUM(ft.amount)    AS total_amount,
                      COUNT(ft.id)      AS transaction_count
                    FROM filtered_transactions ft
                    LEFT JOIN banking.transaction_categories tc ON ft.category_id = tc.id AND tc.deleted_at IS NULL
                    WHERE ft.status IN ('POSTED', 'PENDING')
                      AND ((ft.category_id IS NULL) OR tc.id IS NOT NULL)
                      $applicableToWhereClause
                    GROUP BY
                      (ft.category_id IS NULL),
                      tc.id,
                      tc.name,
                      tc.icon,
                      tc.color,
                      tc.parent_id,
                      tc.applicable_to,
                      tc.is_system,
                      tc.sort_order
                    ORDER BY
                      (ft.category_id IS NULL) ASC,
                      total_amount DESC
                    """.trimIndent(),
                ),
                params +
                    mapOf(
                        "uncategorizedSortOrder" to UNCATEGORIZED_CATEGORY_SORT_ORDER,
                    ),
            ).resultList as List<Array<Any?>>
        return rows.map(::mapCategoryBreakdownRow)
    }

    @Transactional(readOnly = true)
    override fun getTagBreakdown(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<TagBreakdownRow> {
        val query =
            TransactionListQuery(
                accountId = accountId,
                financialAccountId = financialAccountId,
                includedFinancialAccountIds = null,
                belongsToUserId = null,
                belongsToName = null,
                search = null,
                tags = null,
                billId = null,
                categoryIds = null,
                uncategorizedOnly = false,
                creditDebitType =
                    when (applicableTo) {
                        CategoryApplicableTo.INCOME -> io.github.salomax.neotool.banking.domain.transaction.CreditDebitType.CREDIT
                        CategoryApplicableTo.EXPENSE -> io.github.salomax.neotool.banking.domain.transaction.CreditDebitType.DEBIT
                        else -> null
                    },
                types = null,
                startDate = LocalDate.of(year, month, 1),
                endDateExclusive = LocalDate.of(year, month, 1).plusMonths(1),
                limit = 1,
                offset = 0,
            )
        return getFilteredTagBreakdown(query, applicableTo)
    }

    @Transactional(readOnly = true)
    override fun getTagBreakdownForAccounts(
        accountId: UUID,
        includedFinancialAccountIds: List<UUID>,
        year: Int,
        month: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<TagBreakdownRow> {
        if (includedFinancialAccountIds.isEmpty()) return emptyList()
        val query =
            TransactionListQuery(
                accountId = accountId,
                financialAccountId = null,
                includedFinancialAccountIds = includedFinancialAccountIds,
                belongsToUserId = null,
                belongsToName = null,
                search = null,
                tags = null,
                billId = null,
                categoryIds = null,
                uncategorizedOnly = false,
                creditDebitType =
                    when (applicableTo) {
                        CategoryApplicableTo.INCOME -> io.github.salomax.neotool.banking.domain.transaction.CreditDebitType.CREDIT
                        CategoryApplicableTo.EXPENSE -> io.github.salomax.neotool.banking.domain.transaction.CreditDebitType.DEBIT
                        else -> null
                    },
                types = null,
                startDate = LocalDate.of(year, month, 1),
                endDateExclusive = LocalDate.of(year, month, 1).plusMonths(1),
                limit = 1,
                offset = 0,
            )
        return getFilteredTagBreakdown(query, applicableTo)
    }

    @Transactional(readOnly = true)
    override fun getFilteredTagBreakdown(
        query: TransactionListQuery,
        applicableTo: CategoryApplicableTo?,
    ): List<TagBreakdownRow> {
        val base = buildTransactionsBaseSql(query)
        val params = LinkedHashMap(base.params)
        params["applicableTo"] = applicableTo?.name
        @Suppress("UNCHECKED_CAST")
        val rows =
            applyParameters(
                entityManager.createNativeQuery(
                    """
                    WITH filtered_transactions AS (
                    ${base.sql}
                    )
                    SELECT
                      tt.tag            AS tag,
                      SUM(ft.amount)    AS total_amount,
                      COUNT(ft.id)      AS transaction_count
                    FROM filtered_transactions ft
                    JOIN banking.transaction_tags tt
                      ON tt.transaction_id = ft.id
                     AND tt.account_id = ft.account_id
                    WHERE ft.status IN ('POSTED', 'PENDING')
                      AND (CAST(:applicableTo AS text) IS NULL
                           OR ft.credit_debit_type = CASE :applicableTo
                               WHEN 'INCOME' THEN 'CREDIT'
                               WHEN 'EXPENSE' THEN 'DEBIT'
                               ELSE ft.credit_debit_type END)
                    GROUP BY tt.tag
                    ORDER BY total_amount DESC, tt.tag ASC
                    """.trimIndent(),
                ),
                params,
            ).resultList as List<Array<Any?>>

        return rows.map { row ->
            TagBreakdownRow(
                tag = row[0] as String,
                totalAmount = row[1] as BigDecimal,
                transactionCount = (row[2] as Number).toLong(),
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

    @Transactional(readOnly = true)
    override fun findPage(query: TransactionListQuery): TransactionPageResult {
        val startedAt = System.nanoTime()
        val pageSql = buildTransactionsPageSql(query)
        val countSql = buildTransactionsCountSql(query)

        val pageQueryStartedAt = System.nanoTime()
        @Suppress("UNCHECKED_CAST")
        val entities =
            applyParameters(
                entityManager.createNativeQuery(pageSql.sql, TransactionEntity::class.java),
                pageSql.params,
            ).resultList as List<TransactionEntity>
        val pageQueryFinishedAt = System.nanoTime()
        val items = withTags(entities.map(TransactionMapper::toDomain), query.accountId)
        val tagsFinishedAt = System.nanoTime()
        val totalCount =
            (applyParameters(entityManager.createNativeQuery(countSql.sql), countSql.params).singleResult as Number).toLong()
        val countFinishedAt = System.nanoTime()
        if (logger.isDebugEnabled) {
            logger.debug(
                "transactions.repo timings accountId={} offset={} limit={} rows={} totalCount={} pageQueryMs={} tagsMs={} countMs={} totalMs={}",
                query.accountId,
                query.offset,
                query.limit,
                items.size,
                totalCount,
                nanosToMillis(pageQueryFinishedAt - pageQueryStartedAt),
                nanosToMillis(tagsFinishedAt - pageQueryFinishedAt),
                nanosToMillis(countFinishedAt - tagsFinishedAt),
                nanosToMillis(countFinishedAt - startedAt),
            )
        }
        return TransactionPageResult(
            items = items,
            totalCount = totalCount,
        )
    }

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

    @Transactional(readOnly = true)
    override fun countSimilarNonManualTransactions(
        accountId: UUID,
        excludeTransactionId: UUID,
        baseDescription: String,
        creditDebitType: CreditDebitType,
    ): Int =
        (entityManager.createNativeQuery(SIMILAR_NON_MANUAL_COUNT_SQL)
            .setParameter("accountId", accountId)
            .setParameter("excludeTransactionId", excludeTransactionId)
            .setParameter("baseDescription", baseDescription)
            .setParameter("creditDebitType", creditDebitType.name)
            .singleResult as Number)
            .toInt()

    @Transactional(readOnly = true)
    override fun findSimilarNonManualTransactions(
        accountId: UUID,
        excludeTransactionId: UUID,
        baseDescription: String,
        creditDebitType: CreditDebitType,
    ): List<Transaction> {
        @Suppress("UNCHECKED_CAST")
        val entities =
            entityManager.createNativeQuery(SIMILAR_NON_MANUAL_SELECT_SQL, TransactionEntity::class.java)
                .setParameter("accountId", accountId)
                .setParameter("excludeTransactionId", excludeTransactionId)
                .setParameter("baseDescription", baseDescription)
                .setParameter("creditDebitType", creditDebitType.name)
                .resultList as List<TransactionEntity>
        return withTags(entities.map(TransactionMapper::toDomain), accountId)
    }

    @Transactional(readOnly = true)
    override fun existsByInvestmentTransactionId(investmentTransactionId: UUID): Boolean =
        entityRepository.existsByInvestmentTransactionIdAndDeletedAtIsNull(investmentTransactionId)

    @Transactional(readOnly = true)
    override fun existsByImportJobId(importJobId: UUID): Boolean =
        entityRepository.existsByImportJobIdAndDeletedAtIsNull(importJobId)

    @Transactional
    override fun clearProviderTransactionIdForDeleted(
        financialAccountId: UUID,
        providerTransactionId: String,
    ): Int = entityRepository.clearProviderTransactionIdForDeleted(financialAccountId, providerTransactionId)

    @Transactional(readOnly = true)
    override fun findByInvestmentTransactionIds(
        investmentTransactionIds: Collection<UUID>,
        accountId: UUID,
    ): List<Transaction> {
        if (investmentTransactionIds.isEmpty()) return emptyList()
        return entityRepository
            .findByInvestmentTransactionIdInAndAccountIdAndDeletedAtIsNull(
                investmentTransactionIds.distinct().toList(),
                accountId,
            )
            .map(TransactionMapper::toDomain)
    }

    @Transactional(readOnly = true)
    override fun findByIds(
        ids: Collection<UUID>,
        accountId: UUID,
    ): List<Transaction> {
        if (ids.isEmpty()) return emptyList()
        return entityRepository
            .findByIdInAndAccountIdAndDeletedAtIsNull(ids.distinct().toList(), accountId)
            .map(TransactionMapper::toDomain)
    }

    @Transactional(readOnly = true)
    override fun findInvestmentCashTransactionsKeyset(
        accountId: UUID,
        financialAccountId: UUID?,
        afterDate: LocalDate?,
        afterId: UUID?,
        limit: Int,
    ): List<Transaction> {
        val sql =
            StringBuilder(
                """
                SELECT t.id
                FROM banking.transactions t
                INNER JOIN banking.financial_accounts fa ON fa.id = t.financial_account_id AND fa.deleted_at IS NULL
                WHERE t.account_id = CAST(:accountId AS uuid)
                  AND t.deleted_at IS NULL
                  AND fa.type IN ('INVESTMENT', 'BENEFIT')
                  AND t.type IN ('TRANSFER', 'INVESTMENT_RETURN', 'BALANCE_ADJUSTMENT')
                  AND (t.type <> 'TRANSFER' OR t.investment_transaction_id IS NULL)
                """.trimIndent(),
            )
        if (financialAccountId != null) {
            sql.append(" AND t.financial_account_id = CAST(:faId AS uuid) ")
        }
        if (afterDate != null && afterId != null) {
            sql.append(
                " AND (t.date < CAST(:afterDate AS date) OR (t.date = CAST(:afterDate AS date) AND t.id < CAST(:afterId AS uuid))) ",
            )
        }
        sql.append(" ORDER BY t.date DESC, t.id DESC LIMIT :lim ")
        val q = entityManager.createNativeQuery(sql.toString())
        q.setParameter("accountId", accountId.toString())
        if (financialAccountId != null) {
            q.setParameter("faId", financialAccountId.toString())
        }
        if (afterDate != null && afterId != null) {
            q.setParameter("afterDate", afterDate.toString())
            q.setParameter("afterId", afterId.toString())
        }
        q.setParameter("lim", limit)
        @Suppress("UNCHECKED_CAST")
        val rawIds = q.resultList as List<*>
        val ids =
            rawIds.mapNotNull { row ->
                when (row) {
                    is UUID -> row
                    is String -> runCatching { UUID.fromString(row) }.getOrNull()
                    else -> null
                }
            }
        if (ids.isEmpty()) return emptyList()
        val entities = entityRepository.findByIdInAndAccountIdAndDeletedAtIsNull(ids, accountId)
        val byId = entities.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        return withTags(ordered.map(TransactionMapper::toDomain), accountId)
    }

    @Transactional(readOnly = true)
    override fun sumLedgerBalance(
        accountId: UUID,
        financialAccountIds: Collection<UUID>,
    ): BigDecimal {
        val ids = financialAccountIds.toList()
        if (ids.isEmpty()) return BigDecimal.ZERO
        val placeholders = ids.indices.joinToString(",") { ":fid$it" }
        val query =
            entityManager.createNativeQuery(
                """
                SELECT COALESCE(SUM(
                    CASE WHEN credit_debit_type = 'CREDIT' THEN amount ELSE -amount END
                ), 0)
                FROM banking.transactions
                WHERE account_id = CAST(:accountId AS uuid)
                  AND financial_account_id IN ($placeholders)
                  AND deleted_at IS NULL
                  AND status IN ('POSTED','PENDING')
                """.trimIndent(),
            )
                .setParameter("accountId", accountId.toString())
        ids.forEachIndexed { i, uuid ->
            query.setParameter("fid$i", uuid)
        }
        return query.singleResult as BigDecimal
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
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT' AND status IN ('POSTED','PENDING')
                                            AND type <> 'TRANSFER'), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'  AND status IN ('POSTED','PENDING')
                                            AND type <> 'TRANSFER'), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT' AND status = 'FUTURE'), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'  AND status = 'FUTURE'), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT' AND status = 'FUTURE'
                                            AND type NOT IN ('TRANSFER','BALANCE_ADJUSTMENT')), 0),
              COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'  AND status = 'FUTURE'
                                            AND type NOT IN ('TRANSFER','BALANCE_ADJUSTMENT')), 0)
            FROM banking.transactions
            WHERE financial_account_id = CAST(:financialAccountId AS uuid)
              AND date >= make_date(:year, :month, 1)
              AND date < (make_date(:year, :month, 1) + INTERVAL '1 month')
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
              AND date >= make_date(:year, :month, 1)
              AND date < (make_date(:year, :month, 1) + INTERVAL '1 month')
              AND deleted_at IS NULL
            """.trimIndent()

        private val SIMILAR_NON_MANUAL_COUNT_SQL =
            """
            SELECT COUNT(*)
            FROM banking.transactions t
            WHERE t.account_id = CAST(:accountId AS uuid)
              AND t.id <> CAST(:excludeTransactionId AS uuid)
              AND t.deleted_at IS NULL
              AND t.source <> 'MANUAL'
              AND t.credit_debit_type = :creditDebitType
              AND COALESCE(NULLIF(TRIM(t.description_raw), ''), t.description) = :baseDescription
            """.trimIndent()

        private val SIMILAR_NON_MANUAL_SELECT_SQL =
            """
            SELECT t.*
            FROM banking.transactions t
            WHERE t.account_id = CAST(:accountId AS uuid)
              AND t.id <> CAST(:excludeTransactionId AS uuid)
              AND t.deleted_at IS NULL
              AND t.source <> 'MANUAL'
              AND t.credit_debit_type = :creditDebitType
              AND COALESCE(NULLIF(TRIM(t.description_raw), ''), t.description) = :baseDescription
            ORDER BY t.date DESC, t.created_at DESC
            """.trimIndent()

        private const val UNCATEGORIZED_CATEGORY_SORT_ORDER = Int.MAX_VALUE
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
        // Clear the persistence context after the bulk delete to avoid
        // NonUniqueObjectException when re-inserting tags with the same id
        // in the same Hibernate session.
        entityManager.flush()
        entityManager.clear()
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

    private fun buildTransactionsPageSql(query: TransactionListQuery): SqlWithParams {
        val base = buildTransactionsBaseSql(query)
        val params = LinkedHashMap(base.params)
        params["limit"] = query.limit
        params["offset"] = query.offset
        return SqlWithParams(
            sql =
                """
                WITH filtered_transactions AS (
                ${base.sql}
                )
                SELECT *
                FROM filtered_transactions
                ORDER BY date DESC, created_at DESC
                LIMIT :limit
                OFFSET :offset
                """.trimIndent(),
            params = params,
        )
    }

    private fun buildTransactionsCountSql(query: TransactionListQuery): SqlWithParams {
        val base = buildTransactionsBaseSql(query)
        return SqlWithParams(
            sql =
                """
                WITH filtered_transactions AS (
                ${base.sql}
                )
                SELECT COUNT(*)
                FROM filtered_transactions
                """.trimIndent(),
            params = base.params,
        )
    }

    private fun buildTransactionsBaseSql(query: TransactionListQuery): SqlWithParams {
        val params = linkedMapOf<String, Any?>("accountId" to query.accountId)
        val unions =
            if (query.billId != null) {
                listOf(buildBillScopedBranch(query, params))
            } else {
                listOf(
                    buildPeriodBranch(query, params),
                    buildInstallmentWithBillBranch(query, params),
                    buildInstallmentNoBillBranch(query, params),
                )
            }
        return SqlWithParams(
            sql = unions.joinToString("\nUNION ALL\n"),
            params = params,
        )
    }

    private fun buildBillScopedBranch(
        query: TransactionListQuery,
        params: MutableMap<String, Any?>,
    ): String {
        params["billId"] = requireNotNull(query.billId) { "billId is required for bill-scoped queries" }
        val conditions = buildCommonConditions(query, params).toMutableList()
        conditions += "t.bill_id = :billId"
        return buildSelectSql(
            joins = emptyList(),
            conditions = conditions,
        )
    }

    private fun buildPeriodBranch(
        query: TransactionListQuery,
        params: MutableMap<String, Any?>,
    ): String {
        params["startDate"] = requireNotNull(query.startDate) { "startDate is required for period queries" }
        params["endDateExclusive"] = requireNotNull(query.endDateExclusive) { "endDateExclusive is required for period queries" }
        val conditions = buildCommonConditions(query, params).toMutableList()
        conditions += "(t.installment_number IS NULL OR t.installment_number <= 1)"
        conditions += "t.date >= :startDate"
        conditions += "t.date < :endDateExclusive"
        return buildSelectSql(
            joins = emptyList(),
            conditions = conditions,
        )
    }

    // Installments tied to an active bill: filter by the bill's due_date so the transaction
    // appears in the period when the bill is due, not when the purchase was made.
    private fun buildInstallmentWithBillBranch(
        query: TransactionListQuery,
        params: MutableMap<String, Any?>,
    ): String {
        params["startDate"] = requireNotNull(query.startDate) { "startDate is required for period queries" }
        params["endDateExclusive"] = requireNotNull(query.endDateExclusive) { "endDateExclusive is required for period queries" }
        val conditions = buildCommonConditions(query, params).toMutableList()
        conditions += "t.installment_number > 1"
        conditions += "b.due_date >= :startDate"
        conditions += "b.due_date < :endDateExclusive"
        return buildSelectSql(
            joins = listOf("JOIN banking.credit_card_bills b ON b.id = t.bill_id AND b.deleted_at IS NULL"),
            conditions = conditions,
        )
    }

    // Installments with no bill assigned: fall back to the transaction date.
    private fun buildInstallmentNoBillBranch(
        query: TransactionListQuery,
        params: MutableMap<String, Any?>,
    ): String {
        params["startDate"] = requireNotNull(query.startDate) { "startDate is required for period queries" }
        params["endDateExclusive"] = requireNotNull(query.endDateExclusive) { "endDateExclusive is required for period queries" }
        val conditions = buildCommonConditions(query, params).toMutableList()
        conditions += "t.installment_number > 1"
        conditions += "t.bill_id IS NULL"
        conditions += "t.date >= :startDate"
        conditions += "t.date < :endDateExclusive"
        return buildSelectSql(
            joins = emptyList(),
            conditions = conditions,
        )
    }

    private fun buildCommonConditions(
        query: TransactionListQuery,
        params: MutableMap<String, Any?>,
    ): List<String> {
        val conditions = mutableListOf(
            "t.account_id = :accountId",
            "t.deleted_at IS NULL",
        )
        query.financialAccountId?.let {
            params["financialAccountId"] = it
            conditions += "t.financial_account_id = :financialAccountId"
        }
        if (!query.includedFinancialAccountIds.isNullOrEmpty()) {
            conditions += buildInClause("t.financial_account_id", "includedFinancialAccountId", query.includedFinancialAccountIds, params)
        }
        query.belongsToUserId?.let {
            params["belongsToUserId"] = it
            conditions += "t.belongs_to_user_id = :belongsToUserId"
        }
        query.belongsToName?.let {
            params["belongsToName"] = it
            conditions += "t.belongs_to_name = :belongsToName"
        }
        query.search?.let {
            params["searchQuery"] = it
            params["searchLikePattern"] = "%${escapeLikePattern(it)}%"
            conditions +=
                """
                (
                    to_tsvector('simple', t.description) @@ websearch_to_tsquery('simple', :searchQuery)
                    OR t.description ILIKE :searchLikePattern ESCAPE '\'
                )
                """.trimIndent()
        }
        if (query.uncategorizedOnly) {
            conditions += "t.category_id IS NULL"
        } else if (!query.categoryIds.isNullOrEmpty()) {
            conditions += buildInClause("t.category_id", "categoryId", query.categoryIds, params)
        }
        query.creditDebitType?.let {
            params["creditDebitType"] = it.name
            conditions += "t.credit_debit_type = :creditDebitType"
            conditions += "t.type NOT IN ('TRANSFER', 'BALANCE_ADJUSTMENT')"
        }
        if (!query.types.isNullOrEmpty()) {
            conditions += buildInClause("t.type", "type", query.types.map { it.name }, params)
        }
        if (!query.tags.isNullOrEmpty()) {
            conditions +=
                """
                EXISTS (
                    SELECT 1
                    FROM banking.transaction_tags tt
                    WHERE tt.transaction_id = t.id
                      AND tt.account_id = :accountId
                      AND ${buildInClause("tt.tag", "tag", query.tags.toList(), params)}
                )
                """.trimIndent()
        }
        return conditions
    }

    private fun buildSelectSql(
        joins: List<String>,
        conditions: List<String>,
    ): String =
        """
        SELECT t.*
        FROM banking.transactions t
        ${joins.joinToString("\n")}
        WHERE ${conditions.joinToString("\n  AND ")}
        """.trimIndent()

    private fun buildInClause(
        column: String,
        prefix: String,
        values: List<Any>,
        params: MutableMap<String, Any?>,
    ): String {
        require(values.isNotEmpty()) { "IN clause requires at least one value" }
        val placeholders =
            values.mapIndexed { index, value ->
                val key = "${prefix}_$index"
                params[key] = value
                ":$key"
            }
        return "$column IN (${placeholders.joinToString(", ")})"
    }

    private fun applyParameters(
        query: Query,
        params: Map<String, Any?>,
    ): Query {
        params.forEach { (name, value) -> query.setParameter(name, value) }
        return query
    }

    private fun escapeLikePattern(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private fun mapCategoryBreakdownRow(row: Array<Any?>): CategoryBreakdownRow =
        CategoryBreakdownRow(
            categoryId = row[0] as? UUID,
            categoryName = row[1] as? String,
            categoryIcon = row[2] as? String,
            categoryColor = row[3] as? String,
            parentId = row[4] as? UUID,
            applicableTo = CategoryApplicableTo.valueOf(row[5] as String),
            isSystem = row[6] as Boolean,
            sortOrder = (row[7] as Number).toInt(),
            uncategorized = row[8] as Boolean,
            totalAmount = row[9] as BigDecimal,
            transactionCount = (row[10] as Number).toLong(),
        )

    private fun nanosToMillis(nanos: Long): Double = nanos / 1_000_000.0

    private data class SqlWithParams(
        val sql: String,
        val params: Map<String, Any?>,
    )

}
