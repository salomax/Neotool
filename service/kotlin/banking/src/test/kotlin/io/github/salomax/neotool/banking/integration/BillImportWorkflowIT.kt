package io.github.salomax.neotool.banking.integration

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.domain.billimport.ConfirmBillImportCommand
import io.github.salomax.neotool.banking.domain.billimport.ConfirmBillReviewCardCommand
import io.github.salomax.neotool.banking.domain.billimport.ConfirmBillReviewTransactionCommand
import io.github.salomax.neotool.banking.domain.billimport.BillImportReview
import io.github.salomax.neotool.banking.domain.billimport.BillImportJob
import io.github.salomax.neotool.banking.domain.billimport.BillImportJobRepository
import io.github.salomax.neotool.banking.domain.billimport.BillImportJobStatus
import io.github.salomax.neotool.banking.domain.billimport.BillImportService
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.infra.kafka.BillImportStatusConsumer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Bill import workflow IT")
class BillImportWorkflowIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var billImportService: BillImportService

    @Inject
    lateinit var billImportJobRepository: BillImportJobRepository

    @Inject
    lateinit var financialAccountRepository: FinancialAccountRepository

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var creditCardBillService: CreditCardBillService

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var cardAId: UUID
    private lateinit var cardBId: UUID
    private lateinit var statusConsumer: BillImportStatusConsumer

    @BeforeEach
    override fun setUp() {
        super.setUp()
        statusConsumer = BillImportStatusConsumer(billImportService)
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        cardAId = createCardAccount("Card A", "1234")
        cardBId = createCardAccount("Card B", "5678")
    }

    @Test
    fun `status workflow progresses through extracting llm and awaiting review`() {
        val job = createPendingJob()
        val jobId = requireNotNull(job.id)
        val extractedText =
            """
            MARCIO ANTUNES DA ROCHA - 5428 XXXX XXXX 2945
            21/01 PAGAMENTO DE FATURA-INTERNET -99,10
            1 24/01 HORTIFRUTI UNIAO 75,77
            """.trimIndent()

        statusConsumer.onExtractorSubmitted(submittedEvent(jobId))
        assertThat(currentJob(jobId).status).isEqualTo(BillImportJobStatus.EXTRACTING)

        statusConsumer.onExtractorExtracted(
            extractedEvent(
                jobId = jobId,
                extractedText = extractedText,
                metadata = mapOf("page_count" to 2, "tables_found" to 1),
            ),
        )
        val extracted = currentJob(jobId)
        assertThat(extracted.status).isEqualTo(BillImportJobStatus.LLM_PROCESSING)
        assertThat(extracted.extractedText).contains("2945")

        statusConsumer.onBankingReaderDone(
            readerDoneEvent(
                jobId = jobId,
                llmOutput = assistantMetaOutput(),
            ),
        )
        val awaiting = currentJob(jobId)
        assertThat(awaiting.status).isEqualTo(BillImportJobStatus.AWAITING_REVIEW)
        val payload = objectMapper.readValue(awaiting.reviewPayload, BillImportReview::class.java)
        assertThat(payload.cards).hasSize(1)
        assertThat(payload.cards.first().lastFourDigits).isEqualTo("2945")
        assertThat(payload.cards.first().transactions).hasSize(2)
        assertThat(awaiting.assistantResponse).contains("card_blocks")
    }

    @Test
    fun `known deterministic profile moves directly to awaiting review with canonical payload`() {
        val job = createPendingJob()
        val jobId = requireNotNull(job.id)

        statusConsumer.onExtractorSubmitted(submittedEvent(jobId))
        statusConsumer.onExtractorExtracted(
            extractedEvent(
                jobId = jobId,
                extractedText =
                    """
                    Nubank
                    Fatura do cartao final 1234
                    | Data | Descricao | Valor |
                    |---|---|---|
                    | 15/01 | MERCADO EXTRA | 45,90 |
                    | 22/01 | ESTORNO IFOOD | -32,00 |
                    | 24/01 | UBER TRIP 2/3 | 139,50 |
                    Total da fatura: 153,40
                    """.trimIndent(),
                metadata = mapOf("statement_year" to 2026),
                documentType = "CREDIT_CARD_BILL",
            ),
        )

        val awaiting = currentJob(jobId)
        assertThat(awaiting.status).isEqualTo(BillImportJobStatus.AWAITING_REVIEW)
        val canonical = objectMapper.readValue(awaiting.reviewPayload, CanonicalReaderOutput::class.java)
        assertThat(canonical.processorType).isEqualTo(CanonicalProcessorType.DETERMINISTIC)
        assertThat(canonical.readerId).isEqualTo("pdf-reader-nubank-credit-card-v1")
        assertThat(canonical.normalizedRecords).isNotEmpty()
    }

    @Test
    fun `dlq event marks job as FAILED with error message`() {
        val job = createPendingJob()
        val jobId = requireNotNull(job.id)

        statusConsumer.onExtractorDlq(
            mapOf(
                "job_id" to jobId.toString(),
                "error" to "download_failed: 403",
                "callback_context" to callbackContext(jobId),
            ),
        )

        val failed = currentJob(jobId)
        assertThat(failed.status).isEqualTo(BillImportJobStatus.FAILED)
        assertThat(failed.errorMessage).contains("download_failed")
    }

    @Test
    fun `assistant failed event marks job as FAILED with structured error details`() {
        val job = createPendingJob()
        val jobId = requireNotNull(job.id)

        statusConsumer.onExtractorSubmitted(submittedEvent(jobId))
        statusConsumer.onExtractorExtracted(
            extractedEvent(
                jobId = jobId,
                extractedText = "--- Page 1 ---",
                metadata = mapOf("page_count" to 1),
            ),
        )

        statusConsumer.onBankingReaderFailed(
            mapOf(
                "job_id" to jobId.toString(),
                "document_type" to "CREDIT_CARD_BILL",
                "schema_version" to "1.0",
                "error" to
                    mapOf(
                        "code" to "SCHEMA_VALIDATION_FAILED",
                        "message" to "output did not match contract",
                        "retryable" to false,
                    ),
                "error_metadata" to
                    mapOf(
                        "processor_type" to "LLM",
                        "stage" to "OUTPUT_VALIDATION",
                    ),
                "callback_context" to callbackContext(jobId),
            ),
        )

        val failed = currentJob(jobId)
        assertThat(failed.status).isEqualTo(BillImportJobStatus.FAILED)
        assertThat(failed.errorMessage).contains("assistant_pdf_reader_failed")
        assertThat(failed.errorMessage).contains("SCHEMA_VALIDATION_FAILED")
        assertThat(failed.errorDetails).contains("assistant.banking-pdf-reader.failed")
        assertThat(failed.errorDetails).contains("OUTPUT_VALIDATION")
    }

    @Test
    fun `confirm commits 2 cards x 5 transactions and is idempotent on second call`() {
        val job = movePendingJobToAwaitingReview()
        val jobId = requireNotNull(job.id)
        val command = confirmCommand(jobId, excluded = null)

        val first = billImportService.confirmBillImport(accountId, userId, command)
        assertThat(first.status).isEqualTo(BillImportJobStatus.DONE)

        val imported = transactionRepository.findByAccount(accountId).filter { it.importJobId == jobId }
        assertThat(imported).hasSize(10)
        assertThat(imported.map { it.source }.toSet()).isEqualTo(setOf(TransactionSource.PDF))

        val billsA = creditCardBillService.listBills(cardAId, accountId)
        val billsB = creditCardBillService.listBills(cardBId, accountId)
        assertThat(billsA).hasSize(1)
        assertThat(billsB).hasSize(1)

        val second = billImportService.confirmBillImport(accountId, userId, command)
        assertThat(second.status).isEqualTo(BillImportJobStatus.DONE)
        val importedAfterSecond = transactionRepository.findByAccount(accountId).filter { it.importJobId == jobId }
        assertThat(importedAfterSecond).hasSize(10)
    }

    @Test
    fun `confirm excludes deselected transaction`() {
        val job = movePendingJobToAwaitingReview()
        val jobId = requireNotNull(job.id)
        val command = confirmCommand(jobId, excluded = cardAId to 1)

        val result = billImportService.confirmBillImport(accountId, userId, command)
        assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)

        val imported = transactionRepository.findByAccount(accountId).filter { it.importJobId == jobId }
        assertThat(imported).hasSize(9)
    }

    private fun createCardAccount(
        name: String,
        lastFour: String,
    ): UUID {
        val saved =
            financialAccountRepository.save(
                FinancialAccount(
                    accountId = accountId,
                    createdByUserId = userId,
                    type = FinancialAccountType.CREDIT,
                    subtype = FinancialAccountSubtype.CREDIT_CARD,
                    name = name,
                    cardLastFourDigits = lastFour,
                    cardData =
                        CardData(
                            maskedNumber = "**** $lastFour",
                            closingDay = 7,
                            dueDay = 15,
                        ),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        return requireNotNull(saved.id)
    }

    private fun createPendingJob(): BillImportJob =
        billImportJobRepository.save(
            BillImportJob(
                financialAccountId = null,
                accountId = accountId,
                createdByUserId = userId,
                status = BillImportJobStatus.PENDING,
                assetId = UUID.randomUUID(),
                fileName = "fatura.pdf",
                fileHash = UUID.randomUUID().toString().replace("-", ""),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

    private fun movePendingJobToAwaitingReview(): BillImportJob {
        val pending = createPendingJob()
        val jobId = requireNotNull(pending.id)

        statusConsumer.onExtractorSubmitted(submittedEvent(jobId))
        statusConsumer.onExtractorExtracted(
            extractedEvent(
                jobId = jobId,
                extractedText = "--- Page 1 ---",
                metadata = mapOf("page_count" to 1, "tables_found" to 1),
            ),
        )
        statusConsumer.onBankingReaderDone(readerDoneEvent(jobId, llmOutput()))

        return currentJob(jobId)
    }

    private fun currentJob(jobId: UUID): BillImportJob = requireNotNull(billImportService.getJob(jobId, accountId))

    private fun callbackContext(jobId: UUID): Map<String, Any> =
        mapOf(
            "job_id" to jobId.toString(),
            "account_id" to accountId.toString(),
            "user_id" to userId.toString(),
        )

    private fun submittedEvent(jobId: UUID): Map<String, Any?> =
        mapOf(
            "job_id" to jobId.toString(),
            "callback_context" to callbackContext(jobId),
        )

    private fun extractedEvent(
        jobId: UUID,
        extractedText: String,
        metadata: Map<String, Any?>,
        documentType: String? = null,
    ): Map<String, Any?> =
        mapOf(
            "job_id" to jobId.toString(),
            "document_type" to documentType,
            "extracted_text" to extractedText,
            "extraction_metadata" to metadata,
            "callback_context" to callbackContext(jobId),
        )

    private fun readerDoneEvent(
        jobId: UUID,
        llmOutput: Map<String, Any?>,
    ): Map<String, Any?> =
        mapOf(
            "job_id" to jobId.toString(),
            "llm_output" to llmOutput,
            "callback_context" to callbackContext(jobId),
        )

    private fun llmOutput(
        totalMatchesDeclared: Boolean = true,
    ): Map<String, Any?> =
        mapOf(
            "bank_name" to "Nubank",
            "statement_period" to
                mapOf(
                    "from" to "2026-01-08",
                    "to" to "2026-02-07",
                ),
            "cards" to
                listOf(
                    mapOf(
                        "review_card_id" to "card-1",
                        "last_four_digits" to "1234",
                        "holder_name" to "Main",
                        "closing_date" to "2026-02-07",
                        "due_date" to "2026-02-15",
                        "declared_total" to 250.0,
                        "computed_total" to 250.0,
                        "total_matches_declared" to totalMatchesDeclared,
                        "transactions" to emptyList<Map<String, Any?>>(),
                        "confidence" to 0.98,
                    ),
                    mapOf(
                        "review_card_id" to "card-2",
                        "last_four_digits" to "5678",
                        "holder_name" to "Second",
                        "closing_date" to "2026-02-07",
                        "due_date" to "2026-02-15",
                        "declared_total" to 250.0,
                        "computed_total" to 250.0,
                        "total_matches_declared" to totalMatchesDeclared,
                        "transactions" to emptyList<Map<String, Any?>>(),
                        "confidence" to 0.98,
                    ),
                ),
        )

    private fun assistantMetaOutput(): Map<String, Any?> =
        mapOf(
            "bank_name" to "SANTANDER",
            "closing_date" to "2026-02-12",
            "due_date" to "2026-02-22",
            "declared_total" to 151.25,
            "card_blocks" to
                listOf(
                    mapOf(
                        "last_four" to "2945",
                        "declared_total" to null,
                        "anchor_start" to "MARCIO ANTUNES DA ROCHA - 5428 XXXX XXXX 2945",
                    ),
                ),
            "transaction_pattern" to
                mapOf(
                    "date_format" to "DD/MM",
                    "last_four_pattern" to null,
                    "currency_symbols" to listOf("R$"),
                    "decimal_separator" to "comma",
                    "installment_pattern" to null,
                ),
        )

    private fun confirmCommand(
        jobId: UUID,
        excluded: Pair<UUID, Int>?,
    ): ConfirmBillImportCommand {
        val cardATransactions =
            (1..5).map { idx ->
                ConfirmBillReviewTransactionCommand(
                    date = LocalDate.of(2026, 1, 10 + idx),
                    description = "CARD_A_$idx",
                    amount = BigDecimal("10.0$idx"),
                    creditDebitType = CreditDebitType.DEBIT,
                    include = excluded != (cardAId to idx),
                )
            }
        val cardBTransactions =
            (1..5).map { idx ->
                ConfirmBillReviewTransactionCommand(
                    date = LocalDate.of(2026, 1, 20 + idx),
                    description = "CARD_B_$idx",
                    amount = BigDecimal("20.0$idx"),
                    creditDebitType = CreditDebitType.DEBIT,
                    include = excluded != (cardBId to idx),
                )
            }
        return ConfirmBillImportCommand(
            jobId = jobId,
            cards =
                listOf(
                    ConfirmBillReviewCardCommand(
                        reviewCardId = "card-1",
                        financialAccountId = cardAId,
                        transactions = cardATransactions,
                    ),
                    ConfirmBillReviewCardCommand(
                        reviewCardId = "card-2",
                        financialAccountId = cardBId,
                        transactions = cardBTransactions,
                    ),
                ),
        )
    }
}
