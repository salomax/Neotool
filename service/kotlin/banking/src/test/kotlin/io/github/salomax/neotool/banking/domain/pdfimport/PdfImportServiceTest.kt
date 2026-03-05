package io.github.salomax.neotool.banking.domain.pdfimport

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.import.dedup.ImportFingerprintService
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.infra.assets.PdfImportAssetStorage
import io.github.salomax.neotool.banking.infra.assets.PdfImportAssetUploadResult
import io.github.salomax.neotool.banking.infra.kafka.PdfExtractorSubmittedEvent
import io.github.salomax.neotool.banking.infra.kafka.PdfExtractorSubmittedProducer
import io.github.salomax.neotool.common.error.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("PdfImportService")
class PdfImportServiceTest {

    private lateinit var repository: PdfImportJobRepository
    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var importFingerprintService: ImportFingerprintService
    private lateinit var snapshotComputationService: SnapshotComputationService
    private lateinit var creditCardBillService: CreditCardBillService
    private lateinit var pdfImportAssetStorage: PdfImportAssetStorage
    private lateinit var submittedProducer: PdfExtractorSubmittedProducer
    private lateinit var service: PdfImportService

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val now = Instant.now()

    @BeforeEach
    fun setUp() {
        repository = mock()
        financialAccountRepository = mock()
        transactionRepository = mock()
        importFingerprintService = mock()
        snapshotComputationService = mock()
        creditCardBillService = mock()
        pdfImportAssetStorage = mock()
        submittedProducer = mock()
        service = PdfImportService(
            repository = repository,
            financialAccountRepository = financialAccountRepository,
            transactionRepository = transactionRepository,
            importFingerprintService = importFingerprintService,
            snapshotComputationService = snapshotComputationService,
            creditCardBillService = creditCardBillService,
            pdfImportAssetStorage = pdfImportAssetStorage,
            submittedProducer = submittedProducer,
            objectMapper = objectMapper,
        )
    }

    @Nested
    @DisplayName("submit")
    inner class SubmitTests {

        @Test
        fun `submit new PDF creates pending job and publishes extractor event`() {
            val assetId = UUID.randomUUID()
            val fileBytes = validPdfBytes("fatura")
            val savedJobId = UUID.randomUUID()

            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
                listOf(cardAccount(financialAccountId, "1234")),
            )
            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)
            whenever(pdfImportAssetStorage.uploadPdf(eq(accountId), eq("fatura.pdf"), eq(fileBytes))).thenReturn(
                PdfImportAssetUploadResult(assetId = assetId, downloadUrl = "https://assets/download/$assetId"),
            )
            whenever(repository.save(any())).thenAnswer {
                val job = it.getArgument<PdfImportJob>(0)
                job.copy(id = savedJobId)
            }

            val result = service.submitPdfBillImport(
                accountId = accountId,
                userId = userId,
                financialAccountId = financialAccountId,
                fileName = "fatura.pdf",
                fileBytes = fileBytes,
            )

            assertThat(result.id).isEqualTo(savedJobId)
            assertThat(result.status).isEqualTo(PdfImportJobStatus.PENDING)

            val eventCaptor = argumentCaptor<PdfExtractorSubmittedEvent>()
            verify(submittedProducer).publish(eventCaptor.capture())
            val event = eventCaptor.firstValue
            assertThat(event.job_id).isEqualTo(savedJobId)
            assertThat(event.asset_id).isEqualTo(assetId)
            assertThat(event.document_type).isEqualTo("CREDIT_CARD_BILL")
            assertThat(event.callback_context.account_id).isEqualTo(accountId)
            assertThat(event.callback_context.user_id).isEqualTo(userId)
        }

        @Test
        fun `submit with file over 20MB returns validation error and does not upload`() {
            val fileBytes = ByteArray(20 * 1024 * 1024 + 1)
            val header = "%PDF-".toByteArray(Charsets.US_ASCII)
            header.indices.forEach { idx -> fileBytes[idx] = header[idx] }

            assertThrows<ValidationException> {
                service.submitPdfBillImport(
                    accountId = accountId,
                    userId = userId,
                    financialAccountId = null,
                    fileName = "fatura.pdf",
                    fileBytes = fileBytes,
                )
            }

            verify(pdfImportAssetStorage, never()).uploadPdf(any(), any(), any())
            verify(repository, never()).save(any())
        }

        @Test
        fun `submit with invalid PDF signature returns validation error`() {
            assertThrows<ValidationException> {
                service.submitPdfBillImport(
                    accountId = accountId,
                    userId = userId,
                    financialAccountId = null,
                    fileName = "fatura.pdf",
                    fileBytes = "not-a-pdf".toByteArray(),
                )
            }

            verify(pdfImportAssetStorage, never()).uploadPdf(any(), any(), any())
            verify(repository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("confirm")
    inner class ConfirmTests {

        @Test
        fun `confirm happy path inserts transactions recomputes bills and marks DONE`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val cardB = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val billB = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command = confirmCommand(jobId, cardA, cardB)

            whenever(repository.findById(jobId, accountId)).thenReturn(job)
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
                listOf(cardAccount(cardA, "1234"), cardAccount(cardB, "5678")),
            )
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(importFingerprintService.compute(any(), any(), any(), any())).thenAnswer {
                val account = it.getArgument<UUID>(0)
                val date = it.getArgument<LocalDate>(1)
                "$account-$date"
            }
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            whenever(creditCardBillService.resolveBillForTransaction(any(), any(), eq(TransactionType.CARD))).thenAnswer {
                val account = it.getArgument<FinancialAccount>(0)
                if (account.id == cardA) billA else billB
            }
            whenever(transactionRepository.save(any())).thenAnswer {
                val tx = it.getArgument<Transaction>(0)
                tx.copy(id = UUID.randomUUID())
            }

            val result = service.confirmPdfBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(PdfImportJobStatus.DONE)
            verify(transactionRepository, times(10)).save(any())
            verify(creditCardBillService).recomputeBill(billA, accountId)
            verify(creditCardBillService).recomputeBill(billB, accountId)
            verify(snapshotComputationService).recomputeChain(eq(cardA), eq(accountId), eq(2026), eq(1))
            verify(snapshotComputationService).recomputeChain(eq(cardB), eq(accountId), eq(2026), eq(1))
        }

        @Test
        fun `confirm skips transactions marked include false`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command = ConfirmPdfBillImportCommand(
                jobId = jobId,
                cards = listOf(
                    ConfirmPdfCardCommand(
                        lastFourDigits = "1234",
                        financialAccountId = cardA,
                        transactions = listOf(
                            ConfirmPdfTransactionCommand(
                                date = LocalDate.of(2026, 1, 15),
                                description = "MERCADO",
                                amount = BigDecimal("45.90"),
                                creditDebitType = CreditDebitType.DEBIT,
                                include = false,
                            ),
                            ConfirmPdfTransactionCommand(
                                date = LocalDate.of(2026, 1, 20),
                                description = "IFOOD",
                                amount = BigDecimal("12.00"),
                                creditDebitType = CreditDebitType.DEBIT,
                                include = true,
                            ),
                        ),
                    ),
                ),
            )

            whenever(repository.findById(jobId, accountId)).thenReturn(job)
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(cardAccount(cardA, "1234")))
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(importFingerprintService.compute(any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            whenever(creditCardBillService.resolveBillForTransaction(any(), any(), any())).thenReturn(billA)
            whenever(transactionRepository.save(any())).thenAnswer { it.getArgument<Transaction>(0).copy(id = UUID.randomUUID()) }

            val result = service.confirmPdfBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(PdfImportJobStatus.DONE)
            verify(transactionRepository, times(1)).save(any())
        }

        @Test
        fun `confirm silently skips duplicate fingerprint`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command = ConfirmPdfBillImportCommand(
                jobId = jobId,
                cards = listOf(
                    ConfirmPdfCardCommand(
                        lastFourDigits = "1234",
                        financialAccountId = cardA,
                        transactions = listOf(
                            ConfirmPdfTransactionCommand(
                                date = LocalDate.of(2026, 1, 15),
                                description = "MERCADO",
                                amount = BigDecimal("45.90"),
                                creditDebitType = CreditDebitType.DEBIT,
                                include = true,
                            ),
                            ConfirmPdfTransactionCommand(
                                date = LocalDate.of(2026, 1, 20),
                                description = "IFOOD",
                                amount = BigDecimal("12.00"),
                                creditDebitType = CreditDebitType.DEBIT,
                                include = true,
                            ),
                        ),
                    ),
                ),
            )

            whenever(repository.findById(jobId, accountId)).thenReturn(job)
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(cardAccount(cardA, "1234")))
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(importFingerprintService.compute(any(), any(), any(), any())).thenReturn("fp-1", "fp-2")
            whenever(importFingerprintService.isDuplicate(cardA, "fp-1")).thenReturn(true)
            whenever(importFingerprintService.isDuplicate(cardA, "fp-2")).thenReturn(false)
            whenever(creditCardBillService.resolveBillForTransaction(any(), any(), any())).thenReturn(billA)
            whenever(transactionRepository.save(any())).thenAnswer { it.getArgument<Transaction>(0).copy(id = UUID.randomUUID()) }

            val result = service.confirmPdfBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(PdfImportJobStatus.DONE)
            verify(transactionRepository, times(1)).save(any())
        }

        @Test
        fun `confirm with wrong status returns validation error`() {
            val jobId = UUID.randomUUID()
            whenever(repository.findById(jobId, accountId)).thenReturn(
                PdfImportJob(
                    id = jobId,
                    financialAccountId = null,
                    accountId = accountId,
                    createdByUserId = userId,
                    status = PdfImportJobStatus.PENDING,
                    assetId = UUID.randomUUID(),
                    fileName = "fatura.pdf",
                    fileHash = "hash",
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val ex = assertThrows<ValidationException> {
                service.confirmPdfBillImport(
                    accountId,
                    userId,
                    ConfirmPdfBillImportCommand(jobId = jobId, cards = emptyList()),
                )
            }

            assertThat(ex.field).isEqualTo("jobId")
            verify(transactionRepository, never()).save(any())
        }

        @Test
        fun `confirm on DONE is idempotent and returns existing job`() {
            val jobId = UUID.randomUUID()
            val doneJob = PdfImportJob(
                id = jobId,
                financialAccountId = null,
                accountId = accountId,
                createdByUserId = userId,
                status = PdfImportJobStatus.DONE,
                assetId = UUID.randomUUID(),
                fileName = "fatura.pdf",
                fileHash = "hash",
                completedAt = now,
                createdAt = now,
                updatedAt = now,
            )
            whenever(repository.findById(jobId, accountId)).thenReturn(doneJob)

            val result = service.confirmPdfBillImport(
                accountId,
                userId,
                ConfirmPdfBillImportCommand(jobId = jobId, cards = emptyList()),
            )

            assertThat(result.status).isEqualTo(PdfImportJobStatus.DONE)
            verify(repository, never()).update(any())
            verify(transactionRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("card association")
    inner class CardAssociationTests {

        @Test
        fun `markAwaitingReview exact match sets matchedFinancialAccountId`() {
            val jobId = UUID.randomUUID()
            val matchedAccountId = UUID.randomUUID()
            whenever(repository.findById(jobId, accountId)).thenReturn(baseJob(jobId))
            whenever(financialAccountRepository.findCardAccountsByLastFourDigits(accountId, "1234")).thenReturn(
                listOf(cardAccount(matchedAccountId, "1234", name = "Nubank Card")),
            )
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            service.markAwaitingReview(jobId, accountId, llmPayload("1234"))

            val updatedCaptor = argumentCaptor<PdfImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val payload = objectMapper.readValue(updatedCaptor.firstValue.reviewPayload, PdfBillExtractionResult::class.java)
            assertThat(payload.cards).hasSize(1)
            assertThat(payload.cards[0].matchedFinancialAccountId).isEqualTo(matchedAccountId)
            assertThat(payload.cards[0].matchAmbiguous).isFalse()
        }

        @Test
        fun `markAwaitingReview no match keeps association null`() {
            val jobId = UUID.randomUUID()
            whenever(repository.findById(jobId, accountId)).thenReturn(baseJob(jobId))
            whenever(financialAccountRepository.findCardAccountsByLastFourDigits(accountId, "1234")).thenReturn(emptyList())
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            service.markAwaitingReview(jobId, accountId, llmPayload("1234"))

            val updatedCaptor = argumentCaptor<PdfImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val payload = objectMapper.readValue(updatedCaptor.firstValue.reviewPayload, PdfBillExtractionResult::class.java)
            assertThat(payload.cards[0].matchedFinancialAccountId).isNull()
            assertThat(payload.cards[0].matchAmbiguous).isFalse()
            assertThat(payload.cards[0].matchCandidates).isEmpty()
        }

        @Test
        fun `markAwaitingReview multiple matches sets ambiguous and candidates`() {
            val jobId = UUID.randomUUID()
            val candidateA = UUID.randomUUID()
            val candidateB = UUID.randomUUID()
            whenever(repository.findById(jobId, accountId)).thenReturn(baseJob(jobId))
            whenever(financialAccountRepository.findCardAccountsByLastFourDigits(accountId, "1234")).thenReturn(
                listOf(
                    cardAccount(candidateA, "1234", name = "Card A"),
                    cardAccount(candidateB, "1234", name = "Card B"),
                ),
            )
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            service.markAwaitingReview(jobId, accountId, llmPayload("1234"))

            val updatedCaptor = argumentCaptor<PdfImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val payload = objectMapper.readValue(updatedCaptor.firstValue.reviewPayload, PdfBillExtractionResult::class.java)
            assertThat(payload.cards[0].matchedFinancialAccountId).isNull()
            assertThat(payload.cards[0].matchAmbiguous).isTrue()
            assertThat(payload.cards[0].matchCandidates.map { it.id }).containsExactlyInAnyOrder(candidateA, candidateB)
        }
    }

    private fun validPdfBytes(content: String): ByteArray =
        ("%PDF-1.7\\n$content").toByteArray(Charsets.US_ASCII)

    private fun cardAccount(id: UUID, lastFour: String, name: String = "Card $lastFour"): FinancialAccount =
        FinancialAccount(
            id = id,
            accountId = accountId,
            createdByUserId = userId,
            type = FinancialAccountType.CREDIT,
            subtype = FinancialAccountSubtype.CREDIT_CARD,
            name = name,
            cardLastFourDigits = lastFour,
            cardData = CardData(maskedNumber = "**** $lastFour", closingDay = 7, dueDay = 15),
            createdAt = now,
            updatedAt = now,
        )

    private fun baseJob(jobId: UUID): PdfImportJob =
        PdfImportJob(
            id = jobId,
            financialAccountId = null,
            accountId = accountId,
            createdByUserId = userId,
            status = PdfImportJobStatus.LLM_PROCESSING,
            assetId = UUID.randomUUID(),
            fileName = "fatura.pdf",
            fileHash = "hash",
            createdAt = now,
            updatedAt = now,
        )

    private fun awaitingReviewJob(jobId: UUID): PdfImportJob {
        val payload = PdfBillExtractionResult(
            bankName = "Nubank",
            statementPeriod = DateRange(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 2, 7)),
            cards = listOf(
                PdfExtractedCard(lastFourDigits = "1234", transactions = emptyList()),
                PdfExtractedCard(lastFourDigits = "5678", transactions = emptyList()),
            ),
            extractionConfidence = 0.98,
            needsReview = false,
            warnings = emptyList(),
        )
        return PdfImportJob(
            id = jobId,
            financialAccountId = null,
            accountId = accountId,
            createdByUserId = userId,
            status = PdfImportJobStatus.AWAITING_REVIEW,
            assetId = UUID.randomUUID(),
            fileName = "fatura.pdf",
            fileHash = "hash",
            reviewPayload = objectMapper.writeValueAsString(payload),
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun confirmCommand(jobId: UUID, cardA: UUID, cardB: UUID): ConfirmPdfBillImportCommand {
        val txsA = (1..5).map { idx ->
            ConfirmPdfTransactionCommand(
                date = LocalDate.of(2026, 1, 10 + idx),
                description = "Card A tx $idx",
                amount = BigDecimal("10.0$idx"),
                creditDebitType = CreditDebitType.DEBIT,
                include = true,
            )
        }
        val txsB = (1..5).map { idx ->
            ConfirmPdfTransactionCommand(
                date = LocalDate.of(2026, 1, 20 + idx),
                description = "Card B tx $idx",
                amount = BigDecimal("20.0$idx"),
                creditDebitType = CreditDebitType.DEBIT,
                include = true,
            )
        }
        return ConfirmPdfBillImportCommand(
            jobId = jobId,
            cards = listOf(
                ConfirmPdfCardCommand(lastFourDigits = "1234", financialAccountId = cardA, transactions = txsA),
                ConfirmPdfCardCommand(lastFourDigits = "5678", financialAccountId = cardB, transactions = txsB),
            ),
        )
    }

    private fun llmPayload(lastFour: String): Map<String, Any?> =
        mapOf(
            "bank_name" to "Nubank",
            "statement_period" to mapOf(
                "from" to "2026-01-08",
                "to" to "2026-02-07",
            ),
            "cards" to listOf(
                mapOf(
                    "last_four_digits" to lastFour,
                    "holder_name" to "JOAO",
                    "closing_date" to "2026-02-07",
                    "due_date" to "2026-02-15",
                    "declared_total" to 100.0,
                    "computed_total" to 100.0,
                    "total_matches_declared" to true,
                    "transactions" to emptyList<Map<String, Any?>>(),
                    "confidence" to 0.99,
                ),
            ),
            "extraction_confidence" to 0.99,
            "needs_review" to false,
            "warnings" to emptyList<String>(),
        )
}
