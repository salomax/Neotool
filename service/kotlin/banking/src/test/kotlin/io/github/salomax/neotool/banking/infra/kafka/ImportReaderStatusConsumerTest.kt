package io.github.salomax.neotool.banking.infra.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.client.import.messaging.ImportJobCompletedProducer
import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.job.import.ImportReaderStatusConsumer
import io.github.salomax.neotool.banking.model.dto.imports.ImportProcessingResult
import io.github.salomax.neotool.banking.model.dto.imports.ImportReviewPayloadMapper
import io.github.salomax.neotool.banking.repo.import.ImportJobRepository
import io.github.salomax.neotool.banking.service.import.ImportProcessor
import io.github.salomax.neotool.banking.service.import.ImportValidationGate
import io.github.salomax.neotool.banking.service.import.event.ImportEventBus
import io.github.salomax.neotool.banking.service.import.statement.AssistantStatementTransactionExtractor
import io.github.salomax.neotool.banking.service.transaction.SnapshotComputationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("ImportReaderStatusConsumer")
class ImportReaderStatusConsumerTest {
    private lateinit var importJobRepository: ImportJobRepository
    private lateinit var importProcessor: ImportProcessor
    private lateinit var importEventBus: ImportEventBus
    private lateinit var jobCompletedProducer: ImportJobCompletedProducer
    private lateinit var snapshotComputationService: SnapshotComputationService
    private lateinit var statementTransactionExtractor: AssistantStatementTransactionExtractor
    private lateinit var consumer: ImportReaderStatusConsumer
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        importJobRepository = mock()
        importProcessor = mock()
        importEventBus = mock()
        jobCompletedProducer = mock()
        snapshotComputationService = mock()
        statementTransactionExtractor = AssistantStatementTransactionExtractor()
        consumer =
            ImportReaderStatusConsumer(
                importJobRepository = importJobRepository,
                importProcessor = importProcessor,
                importEventBus = importEventBus,
                jobCompletedProducer = jobCompletedProducer,
                snapshotComputationService = snapshotComputationService,
                importValidationGate = ImportValidationGate(),
                statementTransactionExtractor = statementTransactionExtractor,
                objectMapper = objectMapper,
            )
    }

    @Test
    fun `done event processes rows and completes import job`() {
        val job = processingJob()
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importProcessor.process(eq(job), any(), eq(false))).thenReturn(
            ImportProcessingResult(
                imported = emptyList(),
                skipped = 1,
                duplicateCandidates = emptyList(),
                errors = emptyList(),
            ),
        )
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderDone(doneEvent(jobId, job.accountId))

        val updateCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(updateCaptor.capture())
        val updated = updateCaptor.firstValue
        assertThat(updated.status).isEqualTo(ImportJobStatus.COMPLETED)
        assertThat(updated.totalRows).isEqualTo(1)
        assertThat(updated.importedCount).isEqualTo(0)
        assertThat(updated.skippedCount).isEqualTo(1)
        verify(jobCompletedProducer).publish(any())
    }

    @Test
    fun `failed event marks import job as failed with details`() {
        val job = processingJob()
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderFailed(failedEvent(jobId, job.accountId))

        val updateCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(updateCaptor.capture())
        val updated = updateCaptor.firstValue
        assertThat(updated.status).isEqualTo(ImportJobStatus.FAILED)
        assertThat(updated.errorDetails).contains("SCHEMA_VALIDATION_FAILED")
        verify(jobCompletedProducer).publish(any())
    }

    @Test
    fun `done event with low llm confidence marks job as review required without processing`() {
        val job = processingJob()
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderDone(
            doneEvent(
                jobId = jobId,
                accountId = job.accountId,
                parseMetadata =
                    mapOf(
                        "processor_type" to "LLM",
                        "reader_id" to "assistant-banking-reader-v1",
                        "confidence" to 0.42,
                        "warnings" to emptyList<String>(),
                    ),
            ),
        )

        verify(importProcessor, never()).process(any(), any(), any())
        val updateCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(updateCaptor.capture())
        val updated = updateCaptor.firstValue
        assertThat(updated.status).isEqualTo(ImportJobStatus.REVIEW_REQUIRED)
        val reviewPayload = ImportReviewPayloadMapper.fromJson(objectMapper, updated.reviewPayload)
        assertThat(reviewPayload).isNotNull
        assertThat(reviewPayload!!.schemaId).isEqualTo(ImportReviewPayloadMapper.SCHEMA_ID)
        assertThat(reviewPayload.duplicateCandidates).isEmpty()
        assertThat(reviewPayload.gate).isNotNull
        assertThat(reviewPayload.gate!!["gate_reason"]).isEqualTo("llm_low_confidence")
        verify(jobCompletedProducer).publish(any())
    }

    @Test
    fun `done event with invalid llm rows marks job as failed without processing`() {
        val job = processingJob()
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderDone(
            doneEventWithInvalidRow(
                jobId = jobId,
                accountId = job.accountId,
            ),
        )

        verify(importProcessor, never()).process(any(), any(), any())
        val updateCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(updateCaptor.capture())
        val updated = updateCaptor.firstValue
        assertThat(updated.status).isEqualTo(ImportJobStatus.FAILED)
        assertThat(updated.errorDetails).contains("llm_invalid_rows")
        verify(jobCompletedProducer).publish(any())
    }

    @Test
    fun `done event parses wrapped transaction aliases and completes import job`() {
        val job = processingJob()
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importProcessor.process(eq(job), any(), eq(false))).thenReturn(
            ImportProcessingResult(
                imported = emptyList(),
                skipped = 1,
                duplicateCandidates = emptyList(),
                errors = emptyList(),
            ),
        )
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderDone(doneEventWithWrappedEntries(jobId, job.accountId))

        val updateCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(updateCaptor.capture())
        val updated = updateCaptor.firstValue
        assertThat(updated.status).isEqualTo(ImportJobStatus.COMPLETED)
        assertThat(updated.totalRows).isEqualTo(1)
        verify(jobCompletedProducer).publish(any())
    }

    @Test
    fun `done event keeps legitimate duplicate rows`() {
        val job = processingJob()
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importProcessor.process(eq(job), any(), eq(false))).thenReturn(
            ImportProcessingResult(
                imported = emptyList(),
                skipped = 2,
                duplicateCandidates = emptyList(),
                errors = emptyList(),
            ),
        )
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderDone(doneEventWithDuplicateRows(jobId, job.accountId))

        val updateCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(updateCaptor.capture())
        val updated = updateCaptor.firstValue
        assertThat(updated.status).isEqualTo(ImportJobStatus.COMPLETED)
        assertThat(updated.totalRows).isEqualTo(2)
        verify(jobCompletedProducer).publish(any())
    }

    @Test
    fun `done event extracts date-grouped multiline statement rows from parser config`() {
        val extractedText =
            """
            Marcos Afonso Salomão Junior
            CPF •••.885.958-•• Agência 0001 Conta
            13560704-2
            01 DE JANEIRO DE 2025 a 31 DE JANEIRO DE 2025 VALORES EM R$
            07 JAN 2025 Total de saídas - 550,00
            Transferência enviada pelo Pix Antonio Carlos Guidoni - •••.470.428-•• - BCO DO 550,00
            BRASIL S.A. (0001) Agência: 7079 Conta: 35017-6
            08 JAN 2025 Total de saídas - 7.000,00
            Transferência enviada Marcos Afonso Salomao Junior - •••.885.958-•• - XP 1.000,00
            Investimentos Corretora de Câmbio Títulos e
            Valores Mobiliários S.A. (0102) Agência: 1 Conta:
            679873-5
            Transferência enviada pelo Pix LILIAN PRISCILLA ONISHI SALOMAO - •••.672.098- 6.000,00
            •• - BCO DO BRASIL S.A. (0001) Agência: 4851
            Conta: 6263735-5
            """.trimIndent()
        val job = processingJob(extractedText = extractedText)
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importProcessor.process(eq(job), any(), eq(false))).thenReturn(
            ImportProcessingResult(
                imported = emptyList(),
                skipped = 3,
                duplicateCandidates = emptyList(),
                errors = emptyList(),
            ),
        )
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderDone(doneEventWithStatementParserConfig(jobId, job.accountId))

        val rowsCaptor = argumentCaptor<List<ParsedTransaction>>()
        verify(importProcessor).process(eq(job), rowsCaptor.capture(), eq(false))
        assertThat(rowsCaptor.firstValue).hasSize(3)
        assertThat(
            rowsCaptor.firstValue.map { it.date.toString() },
        ).containsExactly("2025-01-07", "2025-01-08", "2025-01-08")
        assertThat(rowsCaptor.firstValue.map { it.amount }).containsExactly(
            "550.00".toBigDecimal(),
            "1000.00".toBigDecimal(),
            "6000.00".toBigDecimal(),
        )
        assertThat(rowsCaptor.firstValue.map { it.creditDebitType }).containsOnly(CreditDebitType.DEBIT)
        assertThat(rowsCaptor.firstValue.map { it.description })
            .anySatisfy { description -> assertThat(description).contains("Antonio Carlos Guidoni", "BRASIL S.A.") }
            .anySatisfy {
                    description ->
                assertThat(description).contains("Marcos Afonso Salomao Junior", "XP Investimentos")
            }
            .anySatisfy {
                    description ->
                assertThat(description).contains("LILIAN PRISCILLA ONISHI SALOMAO", "BCO DO BRASIL S.A.")
            }
    }

    @Test
    fun `done event without parsed statement rows persists assistant response and statement period`() {
        val extractedText =
            """
            01 DE JANEIRO DE 2025 a 31 DE JANEIRO DE 2025 VALORES EM R$
            Conteudo sem linhas de transacao reconheciveis
            """.trimIndent()
        val job = processingJob(extractedText = extractedText)
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderDone(doneEventWithStatementParserConfig(jobId, job.accountId))

        verify(importProcessor, never()).process(any(), any(), any())
        val updateCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(updateCaptor.capture())
        val updated = updateCaptor.firstValue
        assertThat(updated.status).isEqualTo(ImportJobStatus.FAILED)
        assertThat(updated.documentType).isEqualTo("BANK_STATEMENT")
        assertThat(updated.dateFrom).isEqualTo(LocalDate.parse("2025-01-01"))
        assertThat(updated.dateTo).isEqualTo(LocalDate.parse("2025-01-31"))
        assertThat(updated.assistantResponse).contains("\"statement_period\"")
        assertThat(updated.errorDetails).contains("no_transaction_rows")
        verify(jobCompletedProducer).publish(any())
    }

    private fun processingJob(extractedText: String? = null): ImportJob {
        val now = Instant.now()
        return ImportJob(
            id = UUID.randomUUID(),
            financialAccountId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            createdByUserId = UUID.randomUUID(),
            source = ImportSource.AUTO,
            documentType = "BANK_STATEMENT",
            fileName = "planilha.xlsx",
            fileHash = "hash",
            status = ImportJobStatus.PROCESSING,
            extractedText = extractedText,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun doneEvent(
        jobId: UUID,
        accountId: UUID,
        parseMetadata: Map<String, Any?>? = null,
    ): Map<String, Any?> =
        buildMap {
            put("job_id", jobId.toString())
            put("document_type", "BANK_STATEMENT")
            put(
                "llm_output",
                mapOf(
                    "cards" to
                        listOf(
                            mapOf(
                                "transactions" to
                                    listOf(
                                        mapOf(
                                            "date" to "2026-02-03",
                                            "description" to "Salario",
                                            "amount" to 5600.00,
                                            "credit_debit_type" to "CREDIT",
                                        ),
                                    ),
                            ),
                        ),
                ),
            )
            put(
                "callback_context",
                mapOf(
                    "job_id" to jobId.toString(),
                    "account_id" to accountId.toString(),
                    "user_id" to UUID.randomUUID().toString(),
                ),
            )
            if (parseMetadata != null) {
                put("parse_metadata", parseMetadata)
            }
        }

    private fun doneEventWithInvalidRow(
        jobId: UUID,
        accountId: UUID,
    ): Map<String, Any?> =
        mapOf(
            "job_id" to jobId.toString(),
            "document_type" to "BANK_STATEMENT",
            "llm_output" to
                mapOf(
                    "cards" to
                        listOf(
                            mapOf(
                                "transactions" to
                                    listOf(
                                        mapOf(
                                            "date" to "2026-02-03",
                                            "description" to "Salario",
                                            "amount" to 5600.00,
                                            "credit_debit_type" to "CREDIT",
                                        ),
                                        mapOf(
                                            "date" to "",
                                            "description" to "",
                                            "amount" to "abc",
                                            "credit_debit_type" to "UNKNOWN",
                                        ),
                                    ),
                            ),
                        ),
                ),
            "parse_metadata" to
                mapOf(
                    "processor_type" to "LLM",
                    "reader_id" to "assistant-banking-reader-v1",
                    "confidence" to 0.95,
                    "warnings" to emptyList<String>(),
                ),
            "callback_context" to
                mapOf(
                    "job_id" to jobId.toString(),
                    "account_id" to accountId.toString(),
                    "user_id" to UUID.randomUUID().toString(),
                ),
        )

    private fun doneEventWithWrappedEntries(
        jobId: UUID,
        accountId: UUID,
    ): Map<String, Any?> =
        mapOf(
            "job_id" to jobId.toString(),
            "document_type" to "BANK_STATEMENT",
            "llm_output" to
                mapOf(
                    "bank_statement" to
                        mapOf(
                            "entries" to
                                listOf(
                                    mapOf(
                                        "date" to "2026-02-03",
                                        "description" to "Transferencia PIX",
                                        "amount" to 50.0,
                                        "credit_debit_type" to "DEBIT",
                                    ),
                                ),
                        ),
                    "cards" to emptyList<Map<String, Any?>>(),
                ),
            "callback_context" to
                mapOf(
                    "job_id" to jobId.toString(),
                    "account_id" to accountId.toString(),
                    "user_id" to UUID.randomUUID().toString(),
                ),
        )

    private fun doneEventWithDuplicateRows(
        jobId: UUID,
        accountId: UUID,
    ): Map<String, Any?> =
        mapOf(
            "job_id" to jobId.toString(),
            "document_type" to "BANK_STATEMENT",
            "llm_output" to
                mapOf(
                    "transactions" to
                        listOf(
                            mapOf(
                                "date" to "2026-02-03",
                                "description" to "Saque ATM",
                                "amount" to 50.0,
                                "credit_debit_type" to "DEBIT",
                            ),
                            mapOf(
                                "date" to "2026-02-03",
                                "description" to "Saque ATM",
                                "amount" to 50.0,
                                "credit_debit_type" to "DEBIT",
                            ),
                        ),
                    "cards" to emptyList<Map<String, Any?>>(),
                ),
            "callback_context" to
                mapOf(
                    "job_id" to jobId.toString(),
                    "account_id" to accountId.toString(),
                    "user_id" to UUID.randomUUID().toString(),
                ),
        )

    private fun doneEventWithStatementParserConfig(
        jobId: UUID,
        accountId: UUID,
    ): Map<String, Any?> =
        mapOf(
            "job_id" to jobId.toString(),
            "document_type" to "BANK_STATEMENT",
            "llm_output" to
                mapOf(
                    "bank_name" to "Nubank",
                    "statement_period" to mapOf("from" to "2025-01-01", "to" to "2025-01-31"),
                    "parser_config" to
                        mapOf(
                            "layout_type" to "DATE_GROUPED_MULTILINE",
                            "date_format" to "DD_MMM_YYYY",
                            "date_header_pattern" to "^(\\d{2} DE [A-Z]+ DE \\d{4})$",
                            "inline_transaction_date_pattern" to null,
                            "amount_pattern" to "([\\d.,]+)",
                            "decimal_separator" to "comma",
                            "sign_rule" to "KEYWORD_BASED",
                            "ignore_line_kinds" to
                                listOf(
                                    "DAY_TOTAL",
                                    "RUNNING_BALANCE",
                                    "SECTION_HEADER",
                                    "HEADER",
                                    "FOOTER",
                                ),
                            "examples" to
                                listOf(
                                    "Transferência enviada pelo Pix Maria Jose da Silva - •••.311.704-•• - BCO 250,00",
                                ),
                            "default_credit_debit_type" to "DEBIT",
                        ),
                    "warnings" to emptyList<String>(),
                ),
            "callback_context" to
                mapOf(
                    "job_id" to jobId.toString(),
                    "account_id" to accountId.toString(),
                    "user_id" to UUID.randomUUID().toString(),
                ),
        )

    private fun failedEvent(
        jobId: UUID,
        accountId: UUID,
    ): Map<String, Any?> =
        mapOf(
            "job_id" to jobId.toString(),
            "document_type" to "BANK_STATEMENT",
            "error" to
                mapOf(
                    "code" to "SCHEMA_VALIDATION_FAILED",
                    "message" to "output did not match contract",
                    "retryable" to false,
                ),
            "error_metadata" to mapOf("stage" to "OUTPUT_VALIDATION"),
            "callback_context" to
                mapOf(
                    "job_id" to jobId.toString(),
                    "account_id" to accountId.toString(),
                    "user_id" to UUID.randomUUID().toString(),
                ),
        )
}
