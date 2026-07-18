package io.github.salomax.neotool.banking.graphql.resolver

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.graphql.import.ImportResolver
import io.github.salomax.neotool.banking.graphql.import.ImportResultDTO
import io.github.salomax.neotool.banking.graphql.import.TransactionImportDTO
import io.github.salomax.neotool.banking.model.dto.imports.ImportDuplicateCandidate
import io.github.salomax.neotool.banking.model.dto.imports.ImportReviewPayloadMapper
import io.github.salomax.neotool.banking.service.import.ImportJobService
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

@DisplayName("ImportResolver")
class ImportResolverTest {
    private lateinit var importJobService: ImportJobService
    private lateinit var resolver: ImportResolver

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val jobId = UUID.randomUUID()
    private val now = Instant.now()
    private val objectMapper = jacksonObjectMapper().apply { registerModule(JavaTimeModule()) }

    private fun principal() =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "test-token",
            tokenId = "test-token-id",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )

    private fun pendingJob() =
        ImportJob(
            id = jobId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            source = ImportSource.OFX,
            fileName = "export.ofx",
            fileHash = "abc123",
            status = ImportJobStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )

    private fun completedJob() =
        ImportJob(
            id = jobId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            source = ImportSource.NFC_E,
            fileName = null,
            fileHash = "chNFe123",
            status = ImportJobStatus.COMPLETED,
            totalRows = 1,
            importedCount = 1,
            skippedCount = 0,
            duplicateReviewCount = 0,
            errorCount = 0,
            completedAt = now,
            createdAt = now,
            updatedAt = now,
        )

    private fun sampleDuplicateCandidate(rowIndex: Int = 0) =
        ImportDuplicateCandidate(
            rowIndex = rowIndex,
            parsed =
                ParsedTransaction(
                    providerTransactionId = "pid-$rowIndex",
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = BigDecimal("12.34"),
                    date = LocalDate.of(2026, 2, 10),
                    description = "Coffee $rowIndex",
                    currencyCode = "BRL",
                ),
            candidates = emptyList(),
        )

    @BeforeEach
    fun setUp() {
        importJobService = mock()
        resolver = ImportResolver(importJobService)
    }

    @Nested
    @DisplayName("transactionImports")
    inner class TransactionImportsTests {
        @Test
        fun `returns list of DTOs from service`() {
            val jobs = listOf(pendingJob())
            whenever(importJobService.listJobs(anyOrNull(), eq(accountId))).thenReturn(jobs)

            val result = resolver.transactionImports(financialAccountId.toString(), principal())

            assertThat(result).hasSize(1)
            assertThat(result[0].id).isEqualTo(jobId)
            assertThat(result[0].status).isEqualTo(ImportJobStatus.PENDING)
            assertThat(result[0].source).isEqualTo(ImportSource.OFX)
            verify(importJobService).listJobs(eq(financialAccountId), eq(accountId))
        }

        @Test
        fun `passes null financialAccountId when not provided`() {
            whenever(importJobService.listJobs(anyOrNull(), eq(accountId))).thenReturn(emptyList())

            resolver.transactionImports(null, principal())

            verify(importJobService).listJobs(eq(null), eq(accountId))
        }
    }

    @Nested
    @DisplayName("transactionImport")
    inner class TransactionImportTests {
        @Test
        fun `returns DTO when job exists`() {
            val job = pendingJob()
            whenever(importJobService.getJob(eq(jobId), eq(accountId))).thenReturn(job)

            val result = resolver.transactionImport(jobId.toString(), principal())

            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(jobId)
            assertThat(result.status).isEqualTo(ImportJobStatus.PENDING)
            verify(importJobService).getJob(eq(jobId), eq(accountId))
        }

        @Test
        fun `returns null when job not found`() {
            whenever(importJobService.getJob(eq(jobId), eq(accountId))).thenReturn(null)

            val result = resolver.transactionImport(jobId.toString(), principal())

            assertThat(result).isNull()
            verify(importJobService).getJob(eq(jobId), eq(accountId))
        }

        @Test
        fun `maps duplicate candidates from legacy review payload array`() {
            val candidate = sampleDuplicateCandidate(rowIndex = 3)
            val job =
                pendingJob().copy(
                    status = ImportJobStatus.REVIEW_REQUIRED,
                    duplicateReviewCount = 1,
                    reviewPayload = objectMapper.writeValueAsString(listOf(candidate)),
                )
            whenever(importJobService.getJob(eq(jobId), eq(accountId))).thenReturn(job)

            val result = resolver.transactionImport(jobId.toString(), principal())

            assertThat(result).isNotNull
            assertThat(result!!.duplicateCandidates).hasSize(1)
            assertThat(result.duplicateCandidates.first().rowIndex).isEqualTo(3)
            assertThat(result.duplicateCandidates.first().parsed.description).isEqualTo("Coffee 3")
        }

        @Test
        fun `maps duplicate candidates from canonical review payload envelope`() {
            val candidate = sampleDuplicateCandidate(rowIndex = 7)
            val reviewPayload =
                ImportReviewPayloadMapper.toJson(
                    objectMapper = objectMapper,
                    duplicateCandidates = listOf(candidate),
                    gate = mapOf("gate_reason" to "llm_low_confidence"),
                )
            val job =
                pendingJob().copy(
                    status = ImportJobStatus.REVIEW_REQUIRED,
                    duplicateReviewCount = 1,
                    reviewPayload = reviewPayload,
                )
            whenever(importJobService.getJob(eq(jobId), eq(accountId))).thenReturn(job)

            val result = resolver.transactionImport(jobId.toString(), principal())

            assertThat(result).isNotNull
            assertThat(result!!.duplicateCandidates).hasSize(1)
            assertThat(result.duplicateCandidates.first().rowIndex).isEqualTo(7)
            assertThat(result.duplicateCandidates.first().parsed.description).isEqualTo("Coffee 7")
        }

        @Test
        fun `maps review candidates that include full Transaction domain objects`() {
            val existingTxId = UUID.randomUUID()
            val existingTx =
                io.github.salomax.neotool.banking.domain.transaction.Transaction(
                    id = existingTxId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = io.github.salomax.neotool.banking.domain.transaction.TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("42.50"),
                    date = LocalDate.of(2026, 2, 10),
                    description = "Manual NFC-e candidate",
                    source = io.github.salomax.neotool.banking.domain.transaction.TransactionSource.MANUAL,
                    status = io.github.salomax.neotool.banking.domain.transaction.TransactionStatus.POSTED,
                    createdAt = now,
                    updatedAt = now,
                )
            val candidate =
                ImportDuplicateCandidate(
                    rowIndex = 0,
                    parsed =
                        ParsedTransaction(
                            providerTransactionId = "nfce-1",
                            creditDebitType = CreditDebitType.DEBIT,
                            amount = BigDecimal("42.50"),
                            date = LocalDate.of(2026, 2, 10),
                            description = "NFC-e purchase",
                            currencyCode = "BRL",
                        ),
                    candidates = listOf(existingTx),
                )
            val reviewPayload =
                ImportReviewPayloadMapper.toJson(
                    objectMapper = objectMapper,
                    duplicateCandidates = listOf(candidate),
                    gate = mapOf("kind" to "nfce_manual_link"),
                )
            val job =
                pendingJob().copy(
                    status = ImportJobStatus.REVIEW_REQUIRED,
                    duplicateReviewCount = 1,
                    reviewPayload = reviewPayload,
                )
            whenever(importJobService.getJob(eq(jobId), eq(accountId))).thenReturn(job)

            val result = resolver.transactionImport(jobId.toString(), principal())

            assertThat(result).isNotNull
            assertThat(result!!.duplicateCandidates).hasSize(1)
            assertThat(result.duplicateCandidates.first().candidates).hasSize(1)
            assertThat(result.duplicateCandidates.first().candidates.first().id).isEqualTo(existingTxId)
        }
    }

    @Nested
    @DisplayName("importTransactionsFile")
    inner class ImportTransactionsFileTests {
        @Test
        fun `returns ImportResultDTO with PENDING status`() {
            val fileBytes = "small file".toByteArray()
            val fileBase64 = Base64.getEncoder().encodeToString(fileBytes)
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "fileName" to "export.ofx",
                    "fileBase64" to fileBase64,
                )
            val job = pendingJob()
            whenever(
                importJobService.initiateFileImport(
                    eq(financialAccountId),
                    eq(accountId),
                    eq(userId),
                    any(),
                    eq("export.ofx"),
                    anyOrNull(),
                ),
            ).thenReturn(job)

            val result = resolver.importTransactionsFile(input, principal())

            assertThat(result).isInstanceOf(ImportResultDTO::class.java)
            assertThat(result.import.id).isEqualTo(jobId)
            assertThat(result.import.status).isEqualTo(ImportJobStatus.PENDING)
            verify(importJobService).initiateFileImport(
                eq(financialAccountId),
                eq(accountId),
                eq(userId),
                any(),
                eq("export.ofx"),
                anyOrNull(),
            )
        }

        @Test
        fun `throws ValidationException when file exceeds 3 MB`() {
            val threeMbPlus = ByteArray(3 * 1024 * 1024 + 1)
            val fileBase64 = Base64.getEncoder().encodeToString(threeMbPlus)
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "fileName" to "large.ofx",
                    "fileBase64" to fileBase64,
                )

            assertThrows<ValidationException> {
                resolver.importTransactionsFile(input, principal())
            }

            verify(importJobService, never()).initiateFileImport(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
            )
        }

        @Test
        fun `accepts pdf up to 3 MB`() {
            val twoMbPdf = ByteArray(2 * 1024 * 1024) { 1 }
            val fileBase64 = Base64.getEncoder().encodeToString(twoMbPdf)
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "fileName" to "extrato.pdf",
                    "fileBase64" to fileBase64,
                )
            whenever(
                importJobService.initiateFileImport(any(), any(), any(), any(), any(), anyOrNull()),
            ).thenReturn(pendingJob())

            resolver.importTransactionsFile(input, principal())

            verify(importJobService).initiateFileImport(
                eq(financialAccountId),
                eq(accountId),
                eq(userId),
                any(),
                eq("extrato.pdf"),
                anyOrNull(),
            )
        }

        @Test
        fun `throws ValidationException when pdf exceeds 3 MB`() {
            val threeMbPlus = ByteArray(3 * 1024 * 1024 + 1)
            val fileBase64 = Base64.getEncoder().encodeToString(threeMbPlus)
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "fileName" to "extrato.pdf",
                    "fileBase64" to fileBase64,
                )

            assertThrows<ValidationException> {
                resolver.importTransactionsFile(input, principal())
            }

            verify(importJobService, never()).initiateFileImport(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
            )
        }

        @Test
        fun `accepts CSV_AUTO source and forwards enum to service`() {
            val fileBytes = "small file".toByteArray()
            val fileBase64 = Base64.getEncoder().encodeToString(fileBytes)
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "fileName" to "extrato.csv",
                    "source" to "CSV_AUTO",
                    "fileBase64" to fileBase64,
                )
            val job = pendingJob().copy(source = ImportSource.CSV_AUTO, fileName = "extrato.csv")
            whenever(
                importJobService.initiateFileImport(
                    eq(financialAccountId),
                    eq(accountId),
                    eq(userId),
                    any(),
                    eq("extrato.csv"),
                    eq(ImportSource.CSV_AUTO),
                ),
            ).thenReturn(job)

            val result = resolver.importTransactionsFile(input, principal())

            assertThat(result.import.source).isEqualTo(ImportSource.CSV_AUTO)
            verify(importJobService).initiateFileImport(
                eq(financialAccountId),
                eq(accountId),
                eq(userId),
                any(),
                eq("extrato.csv"),
                eq(ImportSource.CSV_AUTO),
            )
        }

        @Test
        fun `throws ValidationException when source is invalid`() {
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "fileName" to "invalid.csv",
                    "source" to "CSV_UNKNOWN",
                    "fileBase64" to Base64.getEncoder().encodeToString("small".toByteArray()),
                )

            assertThrows<ValidationException> {
                resolver.importTransactionsFile(input, principal())
            }

            verify(importJobService, never()).initiateFileImport(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
            )
        }

        @Test
        fun `accepts CSV_SANTANDER source and forwards enum to service`() {
            val fileBytes = "small file".toByteArray()
            val fileBase64 = Base64.getEncoder().encodeToString(fileBytes)
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "fileName" to "santander.csv",
                    "source" to "CSV_SANTANDER",
                    "fileBase64" to fileBase64,
                )
            val job = pendingJob().copy(source = ImportSource.CSV_SANTANDER, fileName = "santander.csv")
            whenever(
                importJobService.initiateFileImport(
                    eq(financialAccountId),
                    eq(accountId),
                    eq(userId),
                    any(),
                    eq("santander.csv"),
                    eq(ImportSource.CSV_SANTANDER),
                ),
            ).thenReturn(job)

            val result = resolver.importTransactionsFile(input, principal())

            assertThat(result.import.source).isEqualTo(ImportSource.CSV_SANTANDER)
            verify(importJobService).initiateFileImport(
                eq(financialAccountId),
                eq(accountId),
                eq(userId),
                any(),
                eq("santander.csv"),
                eq(ImportSource.CSV_SANTANDER),
            )
        }

        @Test
        fun `throws ValidationException when source is not allowed for file import`() {
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "fileName" to "invalid.csv",
                    "source" to "OPEN_FINANCE",
                    "fileBase64" to Base64.getEncoder().encodeToString("small".toByteArray()),
                )

            assertThrows<ValidationException> {
                resolver.importTransactionsFile(input, principal())
            }

            verify(importJobService, never()).initiateFileImport(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
            )
        }
    }

    @Nested
    @DisplayName("importNfce")
    inner class ImportNfceTests {
        @Test
        fun `returns ImportResultDTO with COMPLETED status`() {
            val qrCodeUrl = "https://www.sefaz.rs.gov.br/NFCE/NFCE-COM.aspx?chNFe=123"
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "qrCodeUrl" to qrCodeUrl,
                )
            val job = completedJob()
            whenever(
                importJobService.importNfce(
                    eq(financialAccountId),
                    eq(accountId),
                    eq(userId),
                    eq(qrCodeUrl),
                ),
            ).thenReturn(job)

            val result = resolver.importNfce(input, principal())

            assertThat(result).isInstanceOf(ImportResultDTO::class.java)
            assertThat(result.import.status).isEqualTo(ImportJobStatus.COMPLETED)
            verify(importJobService).importNfce(
                eq(financialAccountId),
                eq(accountId),
                eq(userId),
                eq(qrCodeUrl),
            )
        }

        @Test
        fun `accepts sao paulo nfce qr code url with raw pipe separators`() {
            val qrCodeUrl =
                "https://www.nfce.fazenda.sp.gov.br/qrcode?p=" +
                    "35260357508426001654650010000196351001874400|3|1"
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "qrCodeUrl" to qrCodeUrl,
                )
            val job = completedJob()
            whenever(
                importJobService.importNfce(
                    eq(financialAccountId),
                    eq(accountId),
                    eq(userId),
                    eq(qrCodeUrl),
                ),
            ).thenReturn(job)

            val result = resolver.importNfce(input, principal())

            assertThat(result.import.status).isEqualTo(ImportJobStatus.COMPLETED)
            verify(importJobService).importNfce(
                eq(financialAccountId),
                eq(accountId),
                eq(userId),
                eq(qrCodeUrl),
            )
        }

        @Test
        fun `throws ValidationException when qrCodeUrl is invalid`() {
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "qrCodeUrl" to "not-a-valid-url:::",
                )

            assertThrows<ValidationException> {
                resolver.importNfce(input, principal())
            }

            verify(importJobService, never()).importNfce(
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Nested
    @DisplayName("resolveImportDuplicate")
    inner class ResolveImportDuplicateTests {
        @Test
        fun `returns updated TransactionImportDTO`() {
            val input =
                mapOf(
                    "jobId" to jobId.toString(),
                    "rowIndex" to 0,
                    "resolution" to "KEEP_BOTH",
                )
            val updatedJob = pendingJob().copy(status = ImportJobStatus.REVIEW_REQUIRED)
            whenever(
                importJobService.resolveImportDuplicate(
                    eq(jobId),
                    eq(0),
                    eq(io.github.salomax.neotool.banking.domain.import.DuplicateResolution.KEEP_BOTH),
                    eq(accountId),
                ),
            ).thenReturn(updatedJob)

            val result = resolver.resolveImportDuplicate(input, principal())

            assertThat(result).isInstanceOf(TransactionImportDTO::class.java)
            assertThat(result.id).isEqualTo(jobId)
            verify(importJobService).resolveImportDuplicate(
                eq(jobId),
                eq(0),
                eq(io.github.salomax.neotool.banking.domain.import.DuplicateResolution.KEEP_BOTH),
                eq(accountId),
            )
        }
    }
}
