package io.github.salomax.neotool.banking.domain.import

import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.integration.BankingPostgresIntegrationTest
import io.github.salomax.neotool.banking.repo.import.ImportJobRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.import.ImportProcessor
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("ImportProcessor IT")
class ImportProcessorIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var importProcessor: ImportProcessor

    @Inject
    lateinit var importJobRepository: ImportJobRepository

    @Inject
    lateinit var transactionRepository: TransactionRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private val now = Instant.now()
    private val today = LocalDate.of(2024, 1, 15)

    @BeforeEach
    fun insertTestData() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            userId = UUID.randomUUID()
            accountId = UUID.randomUUID()
            financialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'IT Processor', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    private fun job(source: ImportSource = ImportSource.OFX) =
        ImportJob(
            id = null,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            source = source,
            fileHash = "it-${UUID.randomUUID()}",
            status = ImportJobStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )

    private fun parsed(
        providerId: String? = "fitid-1",
        amount: BigDecimal =
            BigDecimal(
                "10.00",
            ),
        description: String = "Item",
    ) = ParsedTransaction(
        providerTransactionId = providerId,
        creditDebitType = CreditDebitType.DEBIT,
        amount = amount,
        date = today,
        description = description,
        currencyCode = "BRL",
    )

    private fun insertRuleApplicationModeNever() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.user_preferences (user_id, rule_application_mode, created_at, updated_at)
                VALUES ('$userId', 'NEVER', NOW(), NOW())
                ON CONFLICT (user_id) DO UPDATE
                SET rule_application_mode = EXCLUDED.rule_application_mode,
                    updated_at = NOW()
                """.trimIndent(),
            )
        }
    }

    private fun insertCategoryRule(
        pattern: String,
        categoryName: String = "Delivery",
    ) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.transaction_rules (
                  account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system
                )
                SELECT '$accountId', id, '$pattern', 'CONTAINS', 'EXPENSE', 10, false
                FROM banking.transaction_categories
                WHERE name = '$categoryName' AND is_system = true
                LIMIT 1
                """.trimIndent(),
            )
        }
    }

    @Nested
    @DisplayName("process — import path")
    inner class ProcessImportPathTests {
        @Test
        fun `imports rows and sets import_job_id and import_fingerprint on each`() {
            val savedJob = importJobRepository.save(job())
            val rows =
                listOf(
                    parsed(providerId = "fp-a", amount = BigDecimal("25.50"), description = "Coffee"),
                    parsed(providerId = "fp-b", amount = BigDecimal("30.00"), description = "Lunch"),
                )

            val result = importProcessor.process(savedJob, rows)

            assertThat(result.imported).hasSize(2)
            assertThat(result.skipped).isZero()
            assertThat(result.duplicateCandidates).isEmpty()
            assertThat(result.errors).isEmpty()
            result.imported.forEach { tx ->
                assertThat(tx.importJobId).isEqualTo(savedJob.id)
                assertThat(tx.importFingerprint).isNotNull().hasSize(64)
            }
            val inDb = transactionRepository.findByFinancialAccount(financialAccountId, accountId)
            assertThat(inDb).hasSize(2)
            assertThat(inDb.map { it.importJobId }.toSet()).containsExactly(savedJob.id)
            assertThat(inDb.map { it.importFingerprint }.toSet()).allMatch { it != null && it.length == 64 }
        }

        @Test
        fun `applies existing rules during import even when preference is NEVER`() {
            insertRuleApplicationModeNever()
            insertCategoryRule(pattern = "ifood")
            val savedJob = importJobRepository.save(job())

            val result =
                importProcessor.process(
                    savedJob,
                    listOf(
                        parsed(
                            providerId = "rule-never",
                            amount = BigDecimal("42.90"),
                            description = "IFOOD PEDIDO",
                        ),
                    ),
                )

            assertThat(result.imported).hasSize(1)
            assertThat(result.errors).isEmpty()
            assertThat(result.imported.single().categoryId).isNotNull()
            assertThat(result.imported.single().categorySource).isEqualTo(CategorySource.RULE)

            val persisted = transactionRepository.findByFinancialAccount(financialAccountId, accountId).single()
            assertThat(persisted.categoryId).isNotNull()
            assertThat(persisted.categorySource).isEqualTo(CategorySource.RULE)
        }
    }

    @Nested
    @DisplayName("process — Level 1 skip")
    inner class ProcessLevel1SkipTests {
        @Test
        fun `skips row when same provider id and source already exists`() {
            val savedJob = importJobRepository.save(job())
            val providerId = "level1-${UUID.randomUUID()}"
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    status = TransactionStatus.POSTED,
                    source = TransactionSource.OFX,
                    amount = BigDecimal("50.00"),
                    date = today,
                    description = "Existing",
                    providerTransactionId = providerId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val result =
                importProcessor.process(
                    savedJob,
                    listOf(parsed(providerId = providerId, amount = BigDecimal("50.00"), description = "Duplicate")),
                )

            assertThat(result.imported).isEmpty()
            assertThat(result.skipped).isEqualTo(1)
            val inDb = transactionRepository.findByFinancialAccount(financialAccountId, accountId)
            assertThat(inDb).hasSize(1)
        }
    }

    @Nested
    @DisplayName("process — Level 2 duplicate candidates")
    inner class ProcessLevel2Tests {
        @Test
        fun `Level 2 candidates are not inserted and appear in duplicateCandidates`() {
            val savedJob = importJobRepository.save(job(source = ImportSource.OPEN_FINANCE))
            val matchDate = LocalDate.of(2024, 1, 20)
            val matchAmount = BigDecimal("77.00")
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    status = TransactionStatus.POSTED,
                    source = TransactionSource.MANUAL,
                    amount = matchAmount,
                    date = matchDate,
                    description = "Existing MANUAL",
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val rows =
                listOf(
                    ParsedTransaction(
                        providerTransactionId = "ofx-1",
                        creditDebitType = CreditDebitType.DEBIT,
                        amount = matchAmount,
                        date = matchDate,
                        description = "Incoming OPEN_FINANCE",
                        currencyCode = "BRL",
                    ),
                )
            val result = importProcessor.process(savedJob, rows)

            assertThat(result.imported).isEmpty()
            assertThat(result.duplicateCandidates).hasSize(1)
            assertThat(result.duplicateCandidates[0].candidates).hasSize(1)
            assertThat(result.duplicateCandidates[0].candidates[0].source).isEqualTo(TransactionSource.MANUAL)
            val inDb = transactionRepository.findByFinancialAccount(financialAccountId, accountId)
            assertThat(inDb).hasSize(1)
            assertThat(inDb[0].description).isEqualTo("Existing MANUAL")
        }
    }

    @Nested
    @DisplayName("process — errors")
    inner class ProcessErrorsTests {
        @Test
        fun `row that causes exception is added to errors and processing continues`() {
            val savedJob = importJobRepository.save(job())
            val rows =
                listOf(
                    parsed(description = "Good"),
                    ParsedTransaction(
                        providerTransactionId = "bad",
                        creditDebitType = CreditDebitType.DEBIT,
                        amount = BigDecimal("1E20"),
                        date = today,
                        description = "Huge amount",
                        currencyCode = "BRL",
                    ),
                    parsed(providerId = "good-2", description = "Good second"),
                )

            val result = importProcessor.process(savedJob, rows)

            assertThat(result.imported).hasSize(2)
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].rowIndex).isEqualTo(1)
            assertThat(result.errors[0].reason).isNotBlank()
        }
    }

    @Nested
    @DisplayName("process — acceptance scenario")
    inner class ProcessAcceptanceScenarioTests {
        @Test
        fun `100 rows 10 L1 dup 3 L2 cand 2 err yields 85 imp 10 skip 3 cand 2 err`() {
            val savedJob = importJobRepository.save(job(source = ImportSource.OPEN_FINANCE))
            val l1ProviderIds = (0 until 10).map { "accept-fitid-$it" }
            l1ProviderIds.forEach { pid ->
                transactionRepository.save(
                    Transaction(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        status = TransactionStatus.POSTED,
                        source = TransactionSource.OPEN_FINANCE,
                        amount = BigDecimal("1.00"),
                        date = today,
                        description = "L1 duplicate",
                        providerTransactionId = pid,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            val l2Date = LocalDate.of(2024, 1, 25)
            val l2Amounts = listOf(BigDecimal("100.00"), BigDecimal("101.00"), BigDecimal("102.00"))
            l2Amounts.forEach { amt ->
                transactionRepository.save(
                    Transaction(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        status = TransactionStatus.POSTED,
                        source = TransactionSource.MANUAL,
                        amount = amt,
                        date = l2Date,
                        description = "L2 existing",
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

            val rows =
                (0 until 100).map { i ->
                    when {
                        i < 10 ->
                            parsed(
                                providerId = l1ProviderIds[i],
                                amount = BigDecimal("${i + 1}.00"),
                                description = "Row $i",
                            )
                        i in 50..52 ->
                            ParsedTransaction(
                                providerTransactionId = "ofx-l2-$i",
                                creditDebitType = CreditDebitType.DEBIT,
                                amount = l2Amounts[i - 50],
                                date = l2Date,
                                description = "Row $i L2",
                                currencyCode = "BRL",
                            )
                        i == 60 || i == 70 ->
                            ParsedTransaction(
                                providerTransactionId = "ofx-err-$i",
                                creditDebitType = CreditDebitType.DEBIT,
                                amount = BigDecimal("1E30"),
                                date = today,
                                description = "Row $i overflow",
                                currencyCode = "BRL",
                            )
                        else ->
                            parsed(
                                providerId = "ofx-$i",
                                amount = BigDecimal("${i + 1}.00"),
                                description = "Row $i",
                            )
                    }
                }

            val result = importProcessor.process(savedJob, rows)

            assertThat(result.imported).hasSize(85)
            assertThat(result.skipped).isEqualTo(10)
            assertThat(result.duplicateCandidates).hasSize(3)
            assertThat(result.errors).hasSize(2)
            result.imported.forEach { tx ->
                assertThat(tx.importJobId).isEqualTo(savedJob.id)
                assertThat(tx.importFingerprint).isNotNull()
            }
            val inDb = transactionRepository.findByFinancialAccount(financialAccountId, accountId)
            val fromImport = inDb.filter { it.importJobId == savedJob.id }
            assertThat(fromImport).hasSize(85)
        }
    }
}
