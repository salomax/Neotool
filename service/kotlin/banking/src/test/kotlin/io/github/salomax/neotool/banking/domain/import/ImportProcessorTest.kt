package io.github.salomax.neotool.banking.domain.import

import io.github.salomax.neotool.banking.domain.import.dedup.CrossSourceDedupService
import io.github.salomax.neotool.banking.domain.import.dedup.ImportFingerprintService
import io.github.salomax.neotool.banking.domain.import.dedup.SameSourceDedupService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("ImportProcessor")
class ImportProcessorTest {

    private lateinit var sameSourceDedupService: SameSourceDedupService
    private lateinit var importFingerprintService: ImportFingerprintService
    private lateinit var crossSourceDedupService: CrossSourceDedupService
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var processor: ImportProcessor

    private lateinit var jobId: UUID
    private lateinit var financialAccountId: UUID
    private lateinit var accountId: UUID
    private lateinit var createdByUserId: UUID
    private lateinit var job: ImportJob
    private val today = LocalDate.of(2024, 1, 15)
    private val now = Instant.now()

    @BeforeEach
    fun setUp() {
        sameSourceDedupService = mock()
        importFingerprintService = mock()
        crossSourceDedupService = mock()
        transactionRepository = mock()
        processor = ImportProcessor(
            sameSourceDedupService = sameSourceDedupService,
            importFingerprintService = importFingerprintService,
            crossSourceDedupService = crossSourceDedupService,
            transactionRepository = transactionRepository,
        )
        jobId = UUID.randomUUID()
        financialAccountId = UUID.randomUUID()
        accountId = UUID.randomUUID()
        createdByUserId = UUID.randomUUID()
        job = ImportJob(
            id = jobId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = createdByUserId,
            source = ImportSource.OFX,
            fileHash = "hash-1",
            status = ImportJobStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun parsedRow(
        providerId: String? = "fitid-1",
        amount: BigDecimal = BigDecimal("25.50"),
        description: String = "Coffee",
    ) = ParsedTransaction(
        providerTransactionId = providerId,
        creditDebitType = CreditDebitType.DEBIT,
        amount = amount,
        date = today,
        description = description,
        currencyCode = "BRL",
    )

    @Nested
    @DisplayName("process — validation")
    inner class ProcessValidationTests {

        @Test
        fun `throws when job id is null`() {
            val jobWithoutId = job.copy(id = null)

            assertThrows<IllegalArgumentException> {
                processor.process(jobWithoutId, listOf(parsedRow()))
            }
        }
    }

    @Nested
    @DisplayName("process — import path")
    inner class ProcessImportPathTests {

        @Test
        fun `imports row when no Level 1 Level 1b or Level 2 conflict and save returns transaction`() {
            whenever(sameSourceDedupService.isDuplicate(any(), any(), any())).thenReturn(false)
            whenever(importFingerprintService.compute(any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            whenever(crossSourceDedupService.findCandidates(any(), any(), any())).thenReturn(emptyList())
            val savedTx = Transaction(
                id = UUID.randomUUID(),
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = createdByUserId,
                creditDebitType = CreditDebitType.DEBIT,
                type = TransactionType.OTHER_EXPENSE,
                amount = BigDecimal("25.50"),
                date = today,
                description = "Coffee",
                importFingerprint = "fp-1",
                importJobId = jobId,
                createdAt = now,
                updatedAt = now,
            )
            whenever(transactionRepository.save(any())).thenReturn(savedTx)

            val result = processor.process(job, listOf(parsedRow()))

            assertThat(result.imported).hasSize(1)
            assertThat(result.imported[0].id).isEqualTo(savedTx.id)
            assertThat(result.imported[0].importJobId).isEqualTo(jobId)
            assertThat(result.imported[0].importFingerprint).isEqualTo("fp-1")
            assertThat(result.skipped).isZero()
            assertThat(result.duplicateCandidates).isEmpty()
            assertThat(result.errors).isEmpty()
            verify(transactionRepository).save(any())
        }

        @Test
        fun `calls fingerprint compute with amount in cents and normalized description`() {
            whenever(sameSourceDedupService.isDuplicate(any(), any(), any())).thenReturn(false)
            whenever(importFingerprintService.compute(any(), any(), any(), any())).thenReturn("fp-x")
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            whenever(crossSourceDedupService.findCandidates(any(), any(), any())).thenReturn(emptyList())
            whenever(transactionRepository.save(any())).thenAnswer { it.getArgument(0) }

            processor.process(job, listOf(parsedRow(amount = BigDecimal("10.99"), description = "  Foo  ")))

            verify(importFingerprintService).compute(
                financialAccountId = eq(financialAccountId),
                date = eq(today),
                amountCents = eq(1099L),
                normalizedDescription = eq("  Foo  "),
            )
        }
    }

    @Nested
    @DisplayName("process — Level 1 skip")
    inner class ProcessLevel1SkipTests {

        @Test
        fun `skips row when same-source duplicate by providerTransactionId`() {
            whenever(sameSourceDedupService.isDuplicate(eq(financialAccountId), eq("fitid-1"), any())).thenReturn(true)

            val result = processor.process(job, listOf(parsedRow(providerId = "fitid-1")))

            assertThat(result.imported).isEmpty()
            assertThat(result.skipped).isEqualTo(1)
            verify(transactionRepository, never()).save(any())
        }

        @Test
        fun `does not call sameSource when providerTransactionId is null`() {
            whenever(importFingerprintService.compute(any(), any(), any(), any())).thenReturn("fp")
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            whenever(crossSourceDedupService.findCandidates(any(), any(), any())).thenReturn(emptyList())
            whenever(transactionRepository.save(any())).thenAnswer { it.getArgument(0) }

            processor.process(job, listOf(parsedRow(providerId = null)))

            verify(sameSourceDedupService, never()).isDuplicate(any(), any(), any())
            verify(transactionRepository).save(any())
        }
    }

    @Nested
    @DisplayName("process — Level 1b skip")
    inner class ProcessLevel1bSkipTests {

        @Test
        fun `skips row when fingerprint duplicate`() {
            whenever(sameSourceDedupService.isDuplicate(any(), any(), any())).thenReturn(false)
            whenever(importFingerprintService.compute(any(), any(), any(), any())).thenReturn("fp-dup")
            whenever(importFingerprintService.isDuplicate(eq(financialAccountId), eq("fp-dup"))).thenReturn(true)

            val result = processor.process(job, listOf(parsedRow()))

            assertThat(result.imported).isEmpty()
            assertThat(result.skipped).isEqualTo(1)
            verify(transactionRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("process — Level 2 duplicate candidates")
    inner class ProcessLevel2Tests {

        @Test
        fun `adds to duplicateCandidates and does not save when cross-source candidates found`() {
            whenever(sameSourceDedupService.isDuplicate(any(), any(), any())).thenReturn(false)
            whenever(importFingerprintService.compute(any(), any(), any(), any())).thenReturn("fp-2")
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            val existingManual = Transaction(
                id = UUID.randomUUID(),
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = createdByUserId,
                creditDebitType = CreditDebitType.DEBIT,
                type = TransactionType.OTHER_EXPENSE,
                amount = BigDecimal("25.50"),
                date = today,
                description = "Manual entry",
                source = TransactionSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
            whenever(crossSourceDedupService.findCandidates(any(), any(), any())).thenReturn(listOf(existingManual))

            val result = processor.process(job, listOf(parsedRow()))

            assertThat(result.imported).isEmpty()
            assertThat(result.duplicateCandidates).hasSize(1)
            assertThat(result.duplicateCandidates[0].rowIndex).isZero()
            assertThat(result.duplicateCandidates[0].parsed.description).isEqualTo("Coffee")
            assertThat(result.duplicateCandidates[0].candidates).hasSize(1)
            assertThat(result.duplicateCandidates[0].candidates[0].source).isEqualTo(TransactionSource.MANUAL)
            verify(transactionRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("process — errors")
    inner class ProcessErrorsTests {

        @Test
        fun `adds row to errors and continues when save throws`() {
            whenever(sameSourceDedupService.isDuplicate(any(), any(), any())).thenReturn(false)
            whenever(importFingerprintService.compute(any(), any(), any(), any())).thenReturn("fp-a", "fp-b")
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            whenever(crossSourceDedupService.findCandidates(any(), any(), any())).thenReturn(emptyList())
            whenever(transactionRepository.save(any()))
                .thenThrow(RuntimeException("DB constraint"))
                .thenAnswer { it.getArgument(0) }

            val rows = listOf(parsedRow(description = "First"), parsedRow(providerId = "fitid-2", description = "Second"))
            val result = processor.process(job, rows)

            assertThat(result.imported).hasSize(1)
            assertThat(result.imported[0].description).isEqualTo("Second")
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].rowIndex).isZero()
            assertThat(result.errors[0].reason).contains("DB constraint")
        }

        @Test
        fun `exception in one row does not abort processing of subsequent rows`() {
            whenever(sameSourceDedupService.isDuplicate(any(), any(), any())).thenReturn(false)
            whenever(importFingerprintService.compute(any(), any(), any(), any())).thenReturn("fp-1", "fp-2", "fp-3")
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            whenever(crossSourceDedupService.findCandidates(any(), any(), any())).thenReturn(emptyList())
            whenever(transactionRepository.save(any()))
                .thenAnswer { it.getArgument(0) }
                .thenThrow(IllegalStateException("fail"))
                .thenAnswer { it.getArgument(0) }

            val rows = listOf(
                parsedRow(providerId = "id-1", description = "A"),
                parsedRow(providerId = "id-2", description = "B"),
                parsedRow(providerId = "id-3", description = "C"),
            )
            val result = processor.process(job, rows)

            assertThat(result.imported).hasSize(2)
            assertThat(result.imported[0].description).isEqualTo("A")
            assertThat(result.imported[1].description).isEqualTo("C")
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].rowIndex).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("process — acceptance scenario")
    inner class ProcessAcceptanceScenarioTests {

        @Test
        fun `100 rows with 10 L1 duplicates 3 L2 candidates 2 errors yields 85 imported 10 skipped 3 candidates 2 errors`() {
            val rows = (0 until 100).map { i ->
                parsedRow(
                    providerId = "fitid-$i",
                    amount = BigDecimal("${i + 1}.00"),
                    description = "Row $i",
                )
            }
            whenever(sameSourceDedupService.isDuplicate(any(), any(), any())).thenAnswer { invocation ->
                val providerId = invocation.getArgument<String>(1)
                providerId in (0..9).map { "fitid-$it" }
            }
            whenever(importFingerprintService.compute(any(), any(), any(), any())).thenAnswer { invocation ->
                "fp-${invocation.getArgument<Long>(2)}"
            }
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            val l2Indices = setOf(15, 16, 17)
            whenever(crossSourceDedupService.findCandidates(any(), any(), any())).thenAnswer { invocation ->
                val parsed = invocation.getArgument<ParsedTransaction>(1)
                val desc = parsed.description
                val idx = desc.removePrefix("Row ").toIntOrNull() ?: -1
                if (idx in l2Indices) listOf(Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = createdByUserId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = parsed.amount,
                    date = parsed.date,
                    description = "Existing",
                    source = TransactionSource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                )) else emptyList()
            }
            var saveCallCount = 0
            whenever(transactionRepository.save(any())).thenAnswer { invocation ->
                saveCallCount++
                if (saveCallCount == 5 || saveCallCount == 12) throw RuntimeException("Simulated error")
                invocation.getArgument<Transaction>(0).copy(id = UUID.randomUUID())
            }

            val result = processor.process(job, rows)

            assertThat(result.imported).hasSize(85)
            assertThat(result.skipped).isEqualTo(10)
            assertThat(result.duplicateCandidates).hasSize(3)
            assertThat(result.errors).hasSize(2)
        }
    }
}
