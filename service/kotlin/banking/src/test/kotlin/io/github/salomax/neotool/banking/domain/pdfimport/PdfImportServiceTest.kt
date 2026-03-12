package io.github.salomax.neotool.banking.domain.pdfimport

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutputMapper
import io.github.salomax.neotool.banking.domain.import.dedup.ImportFingerprintService
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.infra.assets.PdfImportAssetStorage
import io.github.salomax.neotool.banking.infra.assets.PdfImportAssetUploadResult
import io.github.salomax.neotool.banking.infra.kafka.PdfExtractorSubmittedEvent
import io.github.salomax.neotool.banking.infra.kafka.PdfExtractorSubmittedProducer
import io.github.salomax.neotool.common.error.ConflictException
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
        service =
            PdfImportService(
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

            val result =
                service.submitPdfBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = PdfImportType.CREDIT_CARD_BILL,
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
                    importType = PdfImportType.CREDIT_CARD_BILL,
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
                    importType = PdfImportType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.pdf",
                    fileBytes = "not-a-pdf".toByteArray(),
                )
            }

            verify(pdfImportAssetStorage, never()).uploadPdf(any(), any(), any())
            verify(repository, never()).save(any())
        }

        @Test
        fun `submit retries broken awaiting review job when review payload is missing`() {
            val existingJobId = UUID.randomUUID()
            val assetId = UUID.randomUUID()
            val fileBytes = validPdfBytes("fatura")
            val existingJob =
                PdfImportJob(
                    id = existingJobId,
                    financialAccountId = null,
                    accountId = accountId,
                    createdByUserId = userId,
                    status = PdfImportJobStatus.AWAITING_REVIEW,
                    assetId = UUID.randomUUID(),
                    fileName = "fatura.pdf",
                    fileHash = "hash",
                    reviewPayload = null,
                    createdAt = now.minusSeconds(120),
                    updatedAt = now.minusSeconds(60),
                )

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(existingJob)
            whenever(pdfImportAssetStorage.uploadPdf(eq(accountId), eq("fatura.pdf"), eq(fileBytes))).thenReturn(
                PdfImportAssetUploadResult(assetId = assetId, downloadUrl = "https://assets/download/$assetId"),
            )
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            val result =
                service.submitPdfBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = PdfImportType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.pdf",
                    fileBytes = fileBytes,
                )

            assertThat(result.id).isEqualTo(existingJobId)
            assertThat(result.status).isEqualTo(PdfImportJobStatus.PENDING)
            verify(repository).update(any())
            verify(repository, never()).save(any())
            verify(submittedProducer).publish(any())
        }

        @Test
        fun `submit BANK_STATEMENT requires financialAccountId`() {
            val ex =
                assertThrows<ValidationException> {
                    service.submitPdfBillImport(
                        accountId = accountId,
                        userId = userId,
                        importType = PdfImportType.BANK_STATEMENT,
                        financialAccountId = null,
                        fileName = "extrato.pdf",
                        fileBytes = validPdfBytes("extrato"),
                    )
                }

            assertThat(ex.field).isEqualTo("financialAccountId")
            verify(pdfImportAssetStorage, never()).uploadPdf(any(), any(), any())
        }

        @Test
        fun `submit duplicate completed file returns conflict`() {
            val existingJob =
                PdfImportJob(
                    id = UUID.randomUUID(),
                    importType = PdfImportType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    accountId = accountId,
                    createdByUserId = userId,
                    status = PdfImportJobStatus.DONE,
                    assetId = UUID.randomUUID(),
                    fileName = "fatura.pdf",
                    fileHash = "hash",
                    completedAt = now.minusSeconds(30),
                    createdAt = now.minusSeconds(120),
                    updatedAt = now.minusSeconds(30),
                )
            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(existingJob)

            assertThrows<ConflictException> {
                service.submitPdfBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = PdfImportType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.pdf",
                    fileBytes = validPdfBytes("fatura"),
                )
            }

            verify(pdfImportAssetStorage, never()).uploadPdf(any(), any(), any())
            verify(submittedProducer, never()).publish(any())
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
            whenever(importFingerprintService.compute(any(), any(), any(), any(), any())).thenAnswer {
                val account = it.getArgument<UUID>(0)
                val date = it.getArgument<LocalDate>(1)
                "$account-$date"
            }
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            whenever(
                creditCardBillService.resolveBillForTransaction(any(), any(), eq(TransactionType.CARD)),
            ).thenAnswer {
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
            val command =
                ConfirmPdfBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmPdfCardCommand(
                                lastFourDigits = "1234",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
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
            whenever(
                financialAccountRepository.findByAccountId(accountId),
            ).thenReturn(listOf(cardAccount(cardA, "1234")))
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(importFingerprintService.compute(any(), any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            whenever(creditCardBillService.resolveBillForTransaction(any(), any(), any())).thenReturn(billA)
            whenever(
                transactionRepository.save(any()),
            ).thenAnswer { it.getArgument<Transaction>(0).copy(id = UUID.randomUUID()) }

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
            val command =
                ConfirmPdfBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmPdfCardCommand(
                                lastFourDigits = "1234",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
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
            whenever(
                financialAccountRepository.findByAccountId(accountId),
            ).thenReturn(listOf(cardAccount(cardA, "1234")))
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(importFingerprintService.compute(any(), any(), any(), any(), any())).thenReturn("fp-1", "fp-2")
            whenever(importFingerprintService.isDuplicate(cardA, "fp-1")).thenReturn(true)
            whenever(importFingerprintService.isDuplicate(cardA, "fp-2")).thenReturn(false)
            whenever(creditCardBillService.resolveBillForTransaction(any(), any(), any())).thenReturn(billA)
            whenever(
                transactionRepository.save(any()),
            ).thenAnswer { it.getArgument<Transaction>(0).copy(id = UUID.randomUUID()) }

            val result = service.confirmPdfBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(PdfImportJobStatus.DONE)
            verify(transactionRepository, times(1)).save(any())
        }

        @Test
        fun `confirm bill payment without linked bank saves TRANSFER CREDIT on card only`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command =
                ConfirmPdfBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmPdfCardCommand(
                                lastFourDigits = "1234",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmPdfTransactionCommand(
                                            date = LocalDate.of(2026, 2, 7),
                                            description = "Pagamento em 07 FEV",
                                            amount = BigDecimal("301.65"),
                                            creditDebitType = CreditDebitType.CREDIT,
                                            include = true,
                                            isBillPayment = true,
                                        ),
                                    ),
                            ),
                        ),
                )

            whenever(repository.findById(jobId, accountId)).thenReturn(job)
            // no linkedBankAccountId
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
                listOf(cardAccount(cardA, "1234")),
            )
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(importFingerprintService.compute(any(), any(), any(), any(), any())).thenReturn("fp-payment")
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            whenever(transactionRepository.save(any())).thenAnswer {
                it.getArgument<Transaction>(0).copy(id = UUID.randomUUID())
            }

            val result = service.confirmPdfBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(PdfImportJobStatus.DONE)
            val txCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository, times(1)).save(txCaptor.capture())
            val savedTx = txCaptor.firstValue
            assertThat(savedTx.type).isEqualTo(TransactionType.TRANSFER)
            assertThat(savedTx.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(savedTx.billId).isNull()
            verify(transactionRepository, never()).update(any())
            verify(creditCardBillService, never()).resolveBillForTransaction(any(), any(), any())
        }

        @Test
        fun `confirm bill payment with linked bank creates paired TRANSFER on both accounts`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val linkedBankId = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command =
                ConfirmPdfBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmPdfCardCommand(
                                lastFourDigits = "1234",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmPdfTransactionCommand(
                                            date = LocalDate.of(2026, 2, 7),
                                            description = "Pagamento em 07 FEV",
                                            amount = BigDecimal("301.65"),
                                            creditDebitType = CreditDebitType.CREDIT,
                                            include = true,
                                            isBillPayment = true,
                                        ),
                                    ),
                            ),
                        ),
                )

            whenever(repository.findById(jobId, accountId)).thenReturn(job)
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
                listOf(cardAccount(cardA, "1234", linkedBankAccountId = linkedBankId)),
            )
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(importFingerprintService.compute(any(), any(), any(), any(), any())).thenReturn("fp-payment")
            whenever(importFingerprintService.isDuplicate(any(), any())).thenReturn(false)
            val cardTxId = UUID.randomUUID()
            val bankTxId = UUID.randomUUID()
            whenever(transactionRepository.save(any())).thenAnswer {
                val tx = it.getArgument<Transaction>(0)
                if (tx.financialAccountId == cardA) tx.copy(id = cardTxId) else tx.copy(id = bankTxId)
            }
            whenever(transactionRepository.update(any())).thenAnswer { it.getArgument(0) }

            val result = service.confirmPdfBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(PdfImportJobStatus.DONE)
            val saveCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository, times(2)).save(saveCaptor.capture())

            val cardSide = saveCaptor.allValues.first { it.financialAccountId == cardA }
            assertThat(cardSide.type).isEqualTo(TransactionType.TRANSFER)
            assertThat(cardSide.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(cardSide.billId).isNull()

            val bankSide = saveCaptor.allValues.first { it.financialAccountId == linkedBankId }
            assertThat(bankSide.type).isEqualTo(TransactionType.TRANSFER)
            assertThat(bankSide.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(bankSide.transferPeerTransactionId).isEqualTo(cardTxId)

            val updateCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository).update(updateCaptor.capture())
            assertThat(updateCaptor.firstValue.transferPeerTransactionId).isEqualTo(bankTxId)

            verify(snapshotComputationService).recomputeChain(eq(cardA), eq(accountId), any(), any())
            verify(snapshotComputationService).recomputeChain(eq(linkedBankId), eq(accountId), any(), any())
            verify(creditCardBillService, never()).resolveBillForTransaction(any(), any(), any())
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

            val ex =
                assertThrows<ValidationException> {
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
            val doneJob =
                PdfImportJob(
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

            val result =
                service.confirmPdfBillImport(
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
            val payload =
                objectMapper.readValue(
                    updatedCaptor.firstValue.reviewPayload,
                    PdfBillExtractionResult::class.java,
                )
            assertThat(payload.cards).hasSize(1)
            assertThat(payload.cards[0].matchedFinancialAccountId).isEqualTo(matchedAccountId)
            assertThat(payload.cards[0].matchAmbiguous).isFalse()
        }

        @Test
        fun `markAwaitingReview no match keeps association null`() {
            val jobId = UUID.randomUUID()
            whenever(repository.findById(jobId, accountId)).thenReturn(baseJob(jobId))
            whenever(
                financialAccountRepository.findCardAccountsByLastFourDigits(accountId, "1234"),
            ).thenReturn(emptyList())
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            service.markAwaitingReview(jobId, accountId, llmPayload("1234"))

            val updatedCaptor = argumentCaptor<PdfImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val payload =
                objectMapper.readValue(
                    updatedCaptor.firstValue.reviewPayload,
                    PdfBillExtractionResult::class.java,
                )
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
            val payload =
                objectMapper.readValue(
                    updatedCaptor.firstValue.reviewPayload,
                    PdfBillExtractionResult::class.java,
                )
            assertThat(payload.cards[0].matchedFinancialAccountId).isNull()
            assertThat(payload.cards[0].matchAmbiguous).isTrue()
            assertThat(payload.cards[0].matchCandidates.map { it.id }).containsExactlyInAnyOrder(candidateA, candidateB)
        }

        @Test
        fun `markAwaitingReviewFromCanonical stores canonical payload and preserves card matching`() {
            val jobId = UUID.randomUUID()
            val matchedAccountId = UUID.randomUUID()
            whenever(repository.findById(jobId, accountId)).thenReturn(baseJob(jobId))
            whenever(financialAccountRepository.findCardAccountsByLastFourDigits(accountId, "1234")).thenReturn(
                listOf(cardAccount(matchedAccountId, "1234", name = "Nubank Card")),
            )
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            val output = canonicalReviewOutput()
            service.markAwaitingReviewFromCanonical(jobId, accountId, output)

            val updatedCaptor = argumentCaptor<PdfImportJob>()
            verify(repository).update(updatedCaptor.capture())
            assertThat(updatedCaptor.firstValue.status).isEqualTo(PdfImportJobStatus.AWAITING_REVIEW)

            val storedCanonical =
                objectMapper.readValue(
                    updatedCaptor.firstValue.reviewPayload,
                    CanonicalReaderOutput::class.java,
                )
            assertThat(storedCanonical.processorType).isEqualTo(CanonicalProcessorType.DETERMINISTIC)
            val summary = storedCanonical.normalizedRecords.first()
            assertThat(summary["matched_financial_account_id"].toString()).isEqualTo(matchedAccountId.toString())
            assertThat(summary["match_ambiguous"]).isEqualTo(false)
        }
    }

    @Test
    fun `markFailed stores structured error details when provided`() {
        val jobId = UUID.randomUUID()
        whenever(repository.findById(jobId, accountId)).thenReturn(baseJob(jobId))
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
        val details =
            mapOf(
                "topic" to "assistant.banking-pdf-reader.failed",
                "error" to
                    mapOf(
                        "code" to "SCHEMA_VALIDATION_FAILED",
                        "message" to "output did not match contract",
                        "retryable" to false,
                    ),
                "error_metadata" to mapOf("stage" to "OUTPUT_VALIDATION"),
            )

        val result =
            service.markFailed(
                jobId = jobId,
                accountId = accountId,
                errorMessage = "assistant_pdf_reader_failed: SCHEMA_VALIDATION_FAILED - output did not match contract",
                errorDetails = details,
            )

        assertThat(result).isNotNull
        assertThat(result!!.status).isEqualTo(PdfImportJobStatus.FAILED)
        assertThat(result.errorMessage).contains("SCHEMA_VALIDATION_FAILED")
        assertThat(result.errorDetails).contains("OUTPUT_VALIDATION")
        assertThat(result.errorDetails).contains("assistant.banking-pdf-reader.failed")
    }

    private fun validPdfBytes(content: String): ByteArray = ("%PDF-1.7\\n$content").toByteArray(Charsets.US_ASCII)

    private fun cardAccount(
        id: UUID,
        lastFour: String,
        name: String = "Card $lastFour",
        linkedBankAccountId: UUID? = null,
    ): FinancialAccount =
        FinancialAccount(
            id = id,
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
                    linkedBankAccountId = linkedBankAccountId,
                ),
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
        val payload =
            PdfBillExtractionResult(
                bankName = "Nubank",
                statementPeriod = DateRange(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 2, 7)),
                cards =
                    listOf(
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

    private fun confirmCommand(
        jobId: UUID,
        cardA: UUID,
        cardB: UUID,
    ): ConfirmPdfBillImportCommand {
        val txsA =
            (1..5).map { idx ->
                ConfirmPdfTransactionCommand(
                    date = LocalDate.of(2026, 1, 10 + idx),
                    description = "Card A tx $idx",
                    amount = BigDecimal("10.0$idx"),
                    creditDebitType = CreditDebitType.DEBIT,
                    include = true,
                )
            }
        val txsB =
            (1..5).map { idx ->
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
            cards =
                listOf(
                    ConfirmPdfCardCommand(lastFourDigits = "1234", financialAccountId = cardA, transactions = txsA),
                    ConfirmPdfCardCommand(lastFourDigits = "5678", financialAccountId = cardB, transactions = txsB),
                ),
        )
    }

    private fun llmPayload(lastFour: String): Map<String, Any?> =
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

    private fun canonicalReviewOutput(): CanonicalReaderOutput =
        CanonicalReaderOutput(
            schemaId = CanonicalReaderOutputMapper.SCHEMA_ID,
            schemaVersion = CanonicalReaderOutputMapper.SCHEMA_VERSION,
            processorType = CanonicalProcessorType.DETERMINISTIC,
            readerId = "pdf-reader-nubank-credit-card-v1",
            confidence = 0.98,
            warnings = emptyList(),
            normalizedRecords =
                listOf(
                    mapOf(
                        "record_type" to "PDF_CARD_SUMMARY",
                        "card_last_four_digits" to "1234",
                        "declared_total" to BigDecimal("153.40"),
                        "computed_total" to BigDecimal("153.40"),
                        "total_matches_declared" to true,
                        "confidence" to 0.98,
                    ),
                    mapOf(
                        "record_type" to "PDF_CARD_TRANSACTION",
                        "card_last_four_digits" to "1234",
                        "date" to "2026-01-15",
                        "description" to "MERCADO EXTRA",
                        "amount" to BigDecimal("45.90"),
                        "credit_debit_type" to "DEBIT",
                        "confidence" to 0.98,
                    ),
                ),
            rawMetadata = mapOf("profile_id" to "pdf-nubank-credit-card-bill-v1"),
        )
}
