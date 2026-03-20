package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.transaction.port.AccountMembershipValidationPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("Transaction categorization engine (CAT-12)")
class TransactionCategorizationEngineTest {
    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()

    private val now = Instant.now()
    private val today = LocalDate.now()

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: InMemoryTransactionCategoryRepository
    private lateinit var ruleRepository: InMemoryCategoryRuleRepository
    private lateinit var transactionRuleEngine: DefaultTransactionRuleEngine
    private lateinit var merchantService: MerchantService

    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var accountMembershipValidationPort: AccountMembershipValidationPort
    private lateinit var snapshotComputationService: SnapshotComputationService
    private lateinit var creditCardBillService: CreditCardBillService

    private lateinit var service: TransactionService

    private val ownedAccount =
        FinancialAccount(
            id = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            type = FinancialAccountType.BANK,
            subtype = FinancialAccountSubtype.CONTA_CORRENTE,
            name = "Checking",
            currencyCode = "BRL",
            createdAt = now,
            updatedAt = now,
        )

    @BeforeEach
    fun setUp() {
        transactionRepository = mock()
        categoryRepository = InMemoryTransactionCategoryRepository()
        ruleRepository = InMemoryCategoryRuleRepository()
        transactionRuleEngine = DefaultTransactionRuleEngine(ruleRepository, categoryRepository)
        merchantService = mock()

        financialAccountRepository = mock()
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))

        accountMembershipValidationPort = mock()
        snapshotComputationService = mock()
        creditCardBillService = mock()

        service =
            TransactionService(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                transactionRuleEngine = transactionRuleEngine,
                merchantService = merchantService,
                financialAccountRepository = financialAccountRepository,
                accountMembershipValidationPort = accountMembershipValidationPort,
                snapshotComputationService = snapshotComputationService,
                creditCardBillService = creditCardBillService,
            )
    }

    private fun createCommand(
        description: String,
        categoryId: UUID? = null,
    ) =
        CreateTransactionCommand(
            financialAccountId = financialAccountId,
            creditDebitType = CreditDebitType.DEBIT,
            type = TransactionType.OTHER_EXPENSE,
            amount = BigDecimal("10.00"),
            date = today,
            description = description,
            categoryId = categoryId,
        )

    private fun txExisting(
        txId: UUID,
        description: String,
        categoryId: UUID? = null,
    ) =
        Transaction(
            id = txId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            creditDebitType = CreditDebitType.DEBIT,
            type = TransactionType.OTHER_EXPENSE,
            status = TransactionStatus.POSTED,
            amount = BigDecimal("10.00"),
            currencyCode = "BRL",
            date = today,
            transactionDatetime = null,
            description = description,
            notes = null,
            tags = emptyList(),
            categoryId = categoryId,
            categorySource = null,
            counterpartyName = null,
            counterpartyDocument = null,
            counterpartyBankCode = null,
            counterpartyAccountNumber = null,
            paymentReason = null,
            transferPeerTransactionId = null,
            installmentNumber = null,
            totalInstallments = null,
            cardIdentifier = null,
            billId = null,
            billPostDate = null,
            payeeMcc = null,
            providerTransactionId = null,
            providerAccountId = null,
            importFingerprint = null,
            importJobId = null,
            deletedAt = null,
            createdAt = now,
            updatedAt = now,
        )

    private fun stubSaveAndUpdate() {
        whenever(transactionRepository.save(any())).thenAnswer { invocation ->
            val tx = invocation.arguments[0] as Transaction
            tx.copy(id = UUID.randomUUID())
        }
        whenever(transactionRepository.update(any())).thenAnswer { invocation ->
            invocation.arguments[0] as Transaction
        }
    }

    @Nested
    @DisplayName("Implicit rule learning and evaluation")
    inner class ImplicitLearningTests {
        @Test
        fun `recategorizing transaction learns implicit rule and next create suggests it`() {
            stubSaveAndUpdate()

            val txId = UUID.randomUUID()
            val categoryAlimentacao = UUID.randomUUID()
            categoryRepository.put(
                TransactionCategory(
                    id = categoryAlimentacao,
                    accountId = accountId,
                    parentId = null,
                    name = "Alimentacao",
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val existing = txExisting(txId = txId, description = "PADARIA DO ZE", categoryId = null)
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)

            val updated = service.update(
                id = txId,
                command = UpdateTransactionCommand(categoryId = categoryAlimentacao),
                accountId = accountId,
            )

            assertThat(updated.categoryId).isEqualTo(categoryAlimentacao)
            assertThat(updated.categorySource).isEqualTo(CategorySource.MANUAL)

            val learned =
                ruleRepository.findImplicitByPattern(accountId, "padaria do ze")
            assertThat(learned).isNotNull
            assertThat(learned!!.categoryId).isEqualTo(categoryAlimentacao)
            assertThat(learned.priority).isEqualTo(60)

            val created =
                service.create(
                    command = createCommand(description = "PADARIA DO ZE LTDA", categoryId = null),
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(created.categoryId).isEqualTo(categoryAlimentacao)
            assertThat(created.categorySource).isEqualTo(CategorySource.RULE)
        }

        @Test
        fun `re-categorizing updates existing implicit rule category`() {
            stubSaveAndUpdate()

            val txId = UUID.randomUUID()
            val categoryAlimentacao = UUID.randomUUID()
            val categoryPadaria = UUID.randomUUID()

            categoryRepository.put(
                TransactionCategory(
                    id = categoryAlimentacao,
                    accountId = accountId,
                    parentId = null,
                    name = "Alimentacao",
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            categoryRepository.put(
                TransactionCategory(
                    id = categoryPadaria,
                    accountId = accountId,
                    parentId = null,
                    name = "Padaria",
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val existing = txExisting(txId = txId, description = "PADARIA DO ZE", categoryId = null)
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)

            service.update(
                id = txId,
                command = UpdateTransactionCommand(categoryId = categoryAlimentacao),
                accountId = accountId,
            )
            val implicitAfterFirst =
                ruleRepository.findImplicitByPattern(accountId, "padaria do ze")
            assertThat(implicitAfterFirst).isNotNull
            assertThat(implicitAfterFirst!!.categoryId).isEqualTo(categoryAlimentacao)

            // Update same transaction to a new category.
            val existingAfterFirst = existing.copy(categoryId = categoryAlimentacao, categorySource = CategorySource.MANUAL)
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existingAfterFirst)
            service.update(
                id = txId,
                command = UpdateTransactionCommand(categoryId = categoryPadaria),
                accountId = accountId,
            )

            val implicitAfterSecond =
                ruleRepository.findImplicitByPattern(accountId, "padaria do ze")
            assertThat(implicitAfterSecond).isNotNull
            assertThat(implicitAfterSecond!!.categoryId).isEqualTo(categoryPadaria)

            val created =
                service.create(
                    command = createCommand(description = "PADARIA DO ZE LTDA", categoryId = null),
                    accountId = accountId,
                    userId = userId,
                )
            assertThat(created.categoryId).isEqualTo(categoryPadaria)
            assertThat(created.categorySource).isEqualTo(CategorySource.RULE)
        }

        @Test
        fun `deleted category rules are skipped during evaluation`() {
            stubSaveAndUpdate()

            val txId = UUID.randomUUID()
            val categoryDeleted = UUID.randomUUID()
            categoryRepository.put(
                TransactionCategory(
                    id = categoryDeleted,
                    accountId = accountId,
                    parentId = null,
                    name = "Alvo",
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            whenever(transactionRepository.findById(txId, accountId)).thenReturn(
                txExisting(txId = txId, description = "PADARIA DO ZE", categoryId = null),
            )

            service.update(
                id = txId,
                command = UpdateTransactionCommand(categoryId = categoryDeleted),
                accountId = accountId,
            )

            assertThat(ruleRepository.findImplicitByPattern(accountId, "padaria do ze")).isNotNull

            // Simulate user deleting the category; implicit rule remains but should be skipped.
            categoryRepository.softDelete(categoryDeleted, accountId)

            val created =
                service.create(
                    command = createCommand(description = "PADARIA DO ZE LTDA", categoryId = null),
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(created.categoryId).isNull()
            assertThat(created.categorySource).isNull()
            assertThat(ruleRepository.findImplicitByPattern(accountId, "padaria do ze")).isNotNull
        }

        @Test
        fun `blank description does not create implicit rule`() {
            stubSaveAndUpdate()

            val txId = UUID.randomUUID()
            val categoryAlimentacao = UUID.randomUUID()
            categoryRepository.put(
                TransactionCategory(
                    id = categoryAlimentacao,
                    accountId = accountId,
                    parentId = null,
                    name = "Alimentacao",
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            whenever(transactionRepository.findById(txId, accountId)).thenReturn(
                txExisting(txId = txId, description = "   ", categoryId = null),
            )

            service.update(
                id = txId,
                command = UpdateTransactionCommand(categoryId = categoryAlimentacao),
                accountId = accountId,
            )

            assertThat(ruleRepository.implicitRulesForAccount(accountId)).isEmpty()
        }
    }
}

private class InMemoryTransactionCategoryRepository : TransactionCategoryRepository {
    private val categories = mutableMapOf<UUID, TransactionCategory>()

    fun put(category: TransactionCategory) {
        val id = requireNotNull(category.id)
        categories[id] = category
    }

    override fun findAll(accountId: UUID): List<TransactionCategory> =
        categories.values.filter { it.deletedAt == null && (it.accountId == null || it.accountId == accountId) }

    override fun findById(id: UUID): TransactionCategory? {
        val category = categories[id] ?: return null
        return if (category.deletedAt == null) category else null
    }

    override fun save(category: TransactionCategory): TransactionCategory {
        val id = category.id ?: error("id is required in test repository")
        categories[id] = category
        return category
    }

    override fun existsByNameAndParentIdAndAccountIdAndDeletedAtIsNull(
        name: String,
        parentId: UUID?,
        accountId: UUID,
    ): Boolean =
        categories.values.any { it.deletedAt == null && it.name == name && it.parentId == parentId && it.accountId == accountId }

    override fun maxSortOrder(
        accountId: UUID,
        parentId: UUID?,
    ): Int? =
        categories.values.filter { it.deletedAt == null && it.accountId == accountId && it.parentId == parentId }
            .maxOfOrNull { it.sortOrder }

    override fun softDelete(
        id: UUID,
        accountId: UUID,
    ) {
        val current = categories[id] ?: return
        if (current.accountId != null && current.accountId != accountId) return
        categories[id] = current.copy(deletedAt = Instant.now(), updatedAt = Instant.now())
    }

    override fun softDeleteChildren(
        parentId: UUID,
        accountId: UUID,
    ) {
        val now = Instant.now()
        categories.replaceAll { _, value ->
            if (value.parentId == parentId && (value.accountId == null || value.accountId == accountId)) {
                value.copy(deletedAt = now, updatedAt = now)
            } else {
                value
            }
        }
    }
}

private class InMemoryCategoryRuleRepository : TransactionRuleRepository {
    private val rules = mutableListOf<TransactionRule>()

    override fun findRulesForAccount(accountId: UUID): List<TransactionRule> =
        rules
            .filter { it.deletedAt == null && (it.accountId == null || it.accountId == accountId) }
            .sortedWith(
                compareBy<TransactionRule> { it.priority }
                    .thenByDescending { it.matchCount }
                    .thenByDescending { it.createdAt },
            )

    override fun findUserRulesForAccount(accountId: UUID): List<TransactionRule> =
        rules
            .filter { it.deletedAt == null && it.accountId == accountId && !it.isSystem }
            .sortedWith(
                compareBy<TransactionRule> { it.priority }
                    .thenByDescending { it.matchCount }
                    .thenByDescending { it.createdAt },
            )

    override fun findById(id: UUID): TransactionRule? =
        rules.firstOrNull { it.deletedAt == null && it.id == id }

    override fun save(rule: TransactionRule): TransactionRule {
        val now = Instant.now()
        val id = rule.id ?: UUID.randomUUID()
        val upserted =
            if (rules.any { it.id == id }) {
                rule.copy(id = id, updatedAt = now)
            } else {
                rule.copy(id = id, createdAt = now, updatedAt = now)
            }

        rules.removeIf { it.id == id }
        rules.add(upserted)
        return upserted
    }

    override fun softDelete(
        id: UUID,
        accountId: UUID,
    ) {
        val now = Instant.now()
        val idx = rules.indexOfFirst { it.id == id && it.accountId == accountId }
        if (idx >= 0) {
            rules[idx] = rules[idx].copy(deletedAt = now, updatedAt = now)
        }
    }

    override fun findImplicitByPattern(
        accountId: UUID,
        pattern: String,
    ): TransactionRule? =
        rules.firstOrNull { it.deletedAt == null && it.accountId == accountId && it.isImplicit && it.pattern == pattern }

    override fun incrementMatchCount(id: UUID) {
        val idx = rules.indexOfFirst { it.id == id && it.deletedAt == null }
        if (idx >= 0) {
            rules[idx] = rules[idx].copy(matchCount = rules[idx].matchCount + 1, updatedAt = Instant.now())
        }
    }

    fun implicitRulesForAccount(accountId: UUID): List<TransactionRule> =
        rules.filter { it.deletedAt == null && it.accountId == accountId && it.isImplicit }
}

