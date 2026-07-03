package io.github.salomax.neotool.banking.domain.billimport

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.client.assets.BillImportAssetStorage
import io.github.salomax.neotool.banking.client.assets.BillImportAssetUploadResult
import io.github.salomax.neotool.banking.client.import.extractor.PdfExtractorSubmittedEvent
import io.github.salomax.neotool.banking.client.import.extractor.PdfExtractorSubmittedProducer
import io.github.salomax.neotool.banking.client.import.messaging.AssistantBankingReaderRequestedEvent
import io.github.salomax.neotool.banking.client.import.messaging.AssistantBankingReaderRequestedProducer
import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.domain.preferences.RuleCreationMode
import io.github.salomax.neotool.banking.domain.transaction.BillStatus
import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBill
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.PatternType
import io.github.salomax.neotool.banking.domain.transaction.RuleSuggestion
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionRule
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.model.dto.billimport.ConfirmBillImportCommand
import io.github.salomax.neotool.banking.model.dto.billimport.ConfirmBillReviewCardCommand
import io.github.salomax.neotool.banking.model.dto.billimport.ConfirmBillReviewTransactionCommand
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.billimport.BillImportJobRepository
import io.github.salomax.neotool.banking.repo.transaction.CreditCardBillRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.billimport.AssistantBillMetaValidator
import io.github.salomax.neotool.banking.service.billimport.AssistantBillReviewBuilder
import io.github.salomax.neotool.banking.service.billimport.AssistantBillTransactionExtractor
import io.github.salomax.neotool.banking.service.billimport.BillImportCommitPreparer
import io.github.salomax.neotool.banking.service.billimport.BillImportService
import io.github.salomax.neotool.banking.service.import.CanonicalReaderOutputMapper
import io.github.salomax.neotool.banking.service.import.dedup.ImportFingerprintService
import io.github.salomax.neotool.banking.service.preferences.UserRuleCreationModeService
import io.github.salomax.neotool.banking.service.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.service.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleEngine
import io.github.salomax.neotool.common.error.BusinessRuleException
import io.github.salomax.neotool.common.error.ValidationException
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("BillImportService")
class BillImportServiceTest {
    private lateinit var repository: BillImportJobRepository
    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var importFingerprintService: ImportFingerprintService
    private lateinit var snapshotComputationService: SnapshotComputationService
    private lateinit var creditCardBillService: CreditCardBillService
    private lateinit var creditCardBillRepository: CreditCardBillRepository
    private lateinit var billImportCommitPreparer: BillImportCommitPreparer
    private lateinit var billAssetStorage: BillImportAssetStorage
    private lateinit var submittedProducer: PdfExtractorSubmittedProducer
    private lateinit var assistantRequestProducer: AssistantBankingReaderRequestedProducer
    private lateinit var categorizationService: TransactionRuleEngine
    private lateinit var userRuleCreationModeService: UserRuleCreationModeService
    private lateinit var service: BillImportService

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
        creditCardBillRepository = mock()
        categorizationService = mock()
        userRuleCreationModeService = mock()
        whenever(userRuleCreationModeService.get(userId)).thenReturn(RuleCreationMode.ALWAYS_PROMPT)
        creditCardBillService = spy(CreditCardBillService(creditCardBillRepository, mock()))
        billImportCommitPreparer = BillImportCommitPreparer(creditCardBillService)
        billAssetStorage = mock()
        submittedProducer = mock()
        assistantRequestProducer = mock()
        service =
            BillImportService(
                repository = repository,
                financialAccountRepository = financialAccountRepository,
                transactionRepository = transactionRepository,
                importFingerprintService = importFingerprintService,
                categorizationService = categorizationService,
                userRuleCreationModeService = userRuleCreationModeService,
                snapshotComputationService = snapshotComputationService,
                creditCardBillService = creditCardBillService,
                billImportCommitPreparer = billImportCommitPreparer,
                assistantBillReviewBuilder =
                    AssistantBillReviewBuilder(
                        objectMapper,
                        AssistantBillMetaValidator(),
                        AssistantBillTransactionExtractor(),
                    ),
                billAssetStorage = billAssetStorage,
                submittedProducer = submittedProducer,
                assistantRequestProducer = assistantRequestProducer,
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
            whenever(billAssetStorage.uploadPdf(eq(accountId), eq("fatura.pdf"), eq(fileBytes))).thenReturn(
                BillImportAssetUploadResult(assetId = assetId, downloadUrl = "https://assets/download/$assetId"),
            )
            whenever(repository.save(any())).thenAnswer {
                val job = it.getArgument<BillImportJob>(0)
                job.copy(id = savedJobId)
            }

            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = financialAccountId,
                    fileName = "fatura.pdf",
                    fileBytes = fileBytes,
                )

            assertThat(result.id).isEqualTo(savedJobId)
            assertThat(result.status).isEqualTo(BillImportJobStatus.PENDING)

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
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.pdf",
                    fileBytes = fileBytes,
                )
            }

            verify(billAssetStorage, never()).uploadPdf(any(), any(), any())
            verify(repository, never()).save(any())
        }

        @Test
        fun `submit with invalid PDF signature returns validation error`() {
            assertThrows<ValidationException> {
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.pdf",
                    fileBytes = "not-a-pdf".toByteArray(),
                )
            }

            verify(billAssetStorage, never()).uploadPdf(any(), any(), any())
            verify(repository, never()).save(any())
        }

        @Test
        fun `submit retries broken awaiting review job when review payload is missing`() {
            val existingJobId = UUID.randomUUID()
            val assetId = UUID.randomUUID()
            val fileBytes = validPdfBytes("fatura")
            val existingJob =
                BillImportJob(
                    id = existingJobId,
                    financialAccountId = null,
                    accountId = accountId,
                    createdByUserId = userId,
                    status = BillImportJobStatus.AWAITING_REVIEW,
                    assetId = UUID.randomUUID(),
                    fileName = "fatura.pdf",
                    fileHash = "hash",
                    reviewPayload = null,
                    createdAt = now.minusSeconds(120),
                    updatedAt = now.minusSeconds(60),
                )

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(existingJob)
            whenever(billAssetStorage.uploadPdf(eq(accountId), eq("fatura.pdf"), eq(fileBytes))).thenReturn(
                BillImportAssetUploadResult(assetId = assetId, downloadUrl = "https://assets/download/$assetId"),
            )
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.pdf",
                    fileBytes = fileBytes,
                )

            assertThat(result.id).isEqualTo(existingJobId)
            assertThat(result.status).isEqualTo(BillImportJobStatus.PENDING)
            verify(repository).update(any())
            verify(repository, never()).save(any())
            verify(submittedProducer).publish(any())
        }

        @Test
        fun `submit BANK_STATEMENT requires financialAccountId`() {
            val ex =
                assertThrows<ValidationException> {
                    service.submitBillImport(
                        accountId = accountId,
                        userId = userId,
                        importType = ImportDocumentType.BANK_STATEMENT,
                        financialAccountId = null,
                        fileName = "extrato.pdf",
                        fileBytes = validPdfBytes("extrato"),
                    )
                }

            assertThat(ex.field).isEqualTo("financialAccountId")
            verify(billAssetStorage, never()).uploadPdf(any(), any(), any())
        }

        @Test
        fun `submit DONE PDF job re-enqueues for reimport instead of conflicting`() {
            val existingJobId = UUID.randomUUID()
            val newAssetId = UUID.randomUUID()
            val existingJob =
                BillImportJob(
                    id = existingJobId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    accountId = accountId,
                    createdByUserId = userId,
                    status = BillImportJobStatus.DONE,
                    assetId = UUID.randomUUID(),
                    fileName = "fatura.pdf",
                    fileHash = "hash",
                    completedAt = now.minusSeconds(30),
                    createdAt = now.minusSeconds(120),
                    updatedAt = now.minusSeconds(30),
                )
            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(existingJob)
            whenever(
                billAssetStorage.uploadPdf(eq(accountId), eq("fatura.pdf"), any()),
            ).thenReturn(BillImportAssetUploadResult(assetId = newAssetId, downloadUrl = "https://assets/$newAssetId"))
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.pdf",
                    fileBytes = validPdfBytes("fatura"),
                )

            assertThat(result.id).isEqualTo(existingJobId)
            assertThat(result.status).isEqualTo(BillImportJobStatus.PENDING)
            assertThat(result.completedAt).isNull()
            verify(repository, never()).save(any())
            verify(repository).update(any())
            verify(billAssetStorage).uploadPdf(eq(accountId), eq("fatura.pdf"), any())
            verify(submittedProducer).publish(any())
        }

        @Test
        fun `submit DONE OFX bill job resets to AWAITING_REVIEW for reimport`() {
            val existingJobId = UUID.randomUUID()
            val existingJob =
                BillImportJob(
                    id = existingJobId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    accountId = accountId,
                    createdByUserId = userId,
                    status = BillImportJobStatus.DONE,
                    assetId = UUID.randomUUID(),
                    fileName = "fatura.ofx",
                    fileHash = "hash",
                    completedAt = now.minusSeconds(30),
                    createdAt = now.minusSeconds(120),
                    updatedAt = now.minusSeconds(30),
                )
            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(existingJob)
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            val fileBytes = loadFixture("/fixtures/ofx/ofx_creditcard_sgml_sample.ofx")
            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.ofx",
                    fileBytes = fileBytes,
                )

            assertThat(result.id).isEqualTo(existingJobId)
            assertThat(result.status).isEqualTo(BillImportJobStatus.AWAITING_REVIEW)
            assertThat(result.completedAt).isNull()
            assertThat(result.reviewPayload).isNotNull()
            verify(repository, never()).save(any())
            verify(repository).update(any())
            verify(billAssetStorage, never()).uploadPdf(any(), any(), any())
            verify(submittedProducer, never()).publish(any())
        }

        @Test
        fun `submit DONE CSV bill job resets to AWAITING_REVIEW for reimport`() {
            val existingJobId = UUID.randomUUID()
            val existingJob =
                BillImportJob(
                    id = existingJobId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    accountId = accountId,
                    createdByUserId = userId,
                    status = BillImportJobStatus.DONE,
                    assetId = UUID.randomUUID(),
                    fileName = "fatura.csv",
                    fileHash = "hash",
                    completedAt = now.minusSeconds(30),
                    createdAt = now.minusSeconds(120),
                    updatedAt = now.minusSeconds(30),
                )
            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(existingJob)
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            val fileBytes = loadFixture("/fixtures/csv/nubank_sample.csv")
            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.csv",
                    fileBytes = fileBytes,
                )

            assertThat(result.id).isEqualTo(existingJobId)
            assertThat(result.status).isEqualTo(BillImportJobStatus.AWAITING_REVIEW)
            assertThat(result.completedAt).isNull()
            assertThat(result.reviewPayload).isNotNull()
            verify(repository, never()).save(any())
            verify(repository).update(any())
            verify(billAssetStorage, never()).uploadPdf(any(), any(), any())
            verify(submittedProducer, never()).publish(any())
        }

        @Test
        fun `submit nubank credit card CSV creates awaiting review job`() {
            val fileBytes = loadFixture("/fixtures/csv/nubank_sample.csv")
            val savedJobId = UUID.randomUUID()

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)
            whenever(repository.save(any())).thenAnswer {
                val job = it.getArgument<BillImportJob>(0)
                job.copy(id = savedJobId)
            }

            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.csv",
                    fileBytes = fileBytes,
                )

            assertThat(result.id).isEqualTo(savedJobId)
            assertThat(result.status).isEqualTo(BillImportJobStatus.AWAITING_REVIEW)
            verify(submittedProducer, never()).publish(any())
            verify(billAssetStorage, never()).uploadPdf(any(), any(), any())

            val savedJobCaptor = argumentCaptor<BillImportJob>()
            verify(repository).save(savedJobCaptor.capture())
            val review = objectMapper.readValue(savedJobCaptor.firstValue.reviewPayload, BillImportReview::class.java)
            assertThat(review.cards).hasSize(1)
            assertThat(review.cards.single().reviewCardId).isEqualTo("card-1")
            assertThat(review.cards.single().transactions).isNotEmpty()
        }

        @Test
        fun `submit nubank account CSV as bill returns unsupported format error`() {
            val fileBytes = loadFixture("/fixtures/csv/nubank_account_sample.csv")

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)

            val ex =
                assertThrows<BusinessRuleException> {
                    service.submitBillImport(
                        accountId = accountId,
                        userId = userId,
                        importType = ImportDocumentType.CREDIT_CARD_BILL,
                        financialAccountId = null,
                        fileName = "extrato.csv",
                        fileBytes = fileBytes,
                    )
                }

            assertThat(ex.errorCode).isEqualTo(BankingErrorCode.IMPORT_FILE_FORMAT_NOT_SUPPORTED)
            verify(repository, never()).save(any())
            verify(submittedProducer, never()).publish(any())
            verify(billAssetStorage, never()).uploadPdf(any(), any(), any())
        }

        @Test
        fun `submit XP CSV with holder creates grouped review cards`() {
            val fileBytes = xpCsvWithHolderBytes()
            val savedJobId = UUID.randomUUID()

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)
            whenever(repository.save(any())).thenAnswer {
                val job = it.getArgument<BillImportJob>(0)
                job.copy(id = savedJobId)
            }

            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura-xp.csv",
                    fileBytes = fileBytes,
                )

            assertThat(result.status).isEqualTo(BillImportJobStatus.AWAITING_REVIEW)

            val savedJobCaptor = argumentCaptor<BillImportJob>()
            verify(repository).save(savedJobCaptor.capture())
            val review = objectMapper.readValue(savedJobCaptor.firstValue.reviewPayload, BillImportReview::class.java)

            assertThat(review.cards).hasSize(2)
            assertThat(review.cards.map { it.holderName }).containsExactlyInAnyOrder("Joao Silva", "Maria Souza")
            assertThat(review.cards.flatMap { it.transactions }).hasSize(3)
            assertThat(review.cards.map { it.closingDate }).containsOnly(LocalDate.of(2026, 3, 2))
        }

        @Test
        fun `submit XP CSV with malformed row returns validation error`() {
            val fileBytes =
                """
                Data;Estabelecimento;Valor;Parcela
                01/03/2026;Mercado Extra;R$ 100,00;-
                invalid-date;Farmacia;R$ 50,00;-
                """.trimIndent().toByteArray(Charsets.UTF_8)

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)

            val ex =
                assertThrows<ValidationException> {
                    service.submitBillImport(
                        accountId = accountId,
                        userId = userId,
                        importType = ImportDocumentType.CREDIT_CARD_BILL,
                        financialAccountId = null,
                        fileName = "fatura-xp.csv",
                        fileBytes = fileBytes,
                    )
                }

            assertThat(ex.field).isEqualTo("fileBase64")
            verify(repository, never()).save(any())
        }

        @Test
        fun `submit XP CSV without holder creates single review card`() {
            val fileBytes = xpCsvWithoutHolderBytes()
            val savedJobId = UUID.randomUUID()

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)
            whenever(repository.save(any())).thenAnswer {
                val job = it.getArgument<BillImportJob>(0)
                job.copy(id = savedJobId)
            }

            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura-xp.csv",
                    fileBytes = fileBytes,
                )

            assertThat(result.status).isEqualTo(BillImportJobStatus.AWAITING_REVIEW)

            val savedJobCaptor = argumentCaptor<BillImportJob>()
            verify(repository).save(savedJobCaptor.capture())
            val review = objectMapper.readValue(savedJobCaptor.firstValue.reviewPayload, BillImportReview::class.java)

            assertThat(review.cards).hasSize(1)
            assertThat(review.cards.single().holderName).isNull()
            assertThat(review.cards.single().transactions).hasSize(2)
        }

        @Test
        fun `submit XP CSV refund reduces computed total`() {
            val fileBytes = xpCsvWithRefundBytes()
            val savedJobId = UUID.randomUUID()

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)
            whenever(repository.save(any())).thenAnswer {
                val job = it.getArgument<BillImportJob>(0)
                job.copy(id = savedJobId)
            }

            service.submitBillImport(
                accountId = accountId,
                userId = userId,
                importType = ImportDocumentType.CREDIT_CARD_BILL,
                financialAccountId = null,
                fileName = "fatura-xp.csv",
                fileBytes = fileBytes,
            )

            val savedJobCaptor = argumentCaptor<BillImportJob>()
            verify(repository).save(savedJobCaptor.capture())
            val review = objectMapper.readValue(savedJobCaptor.firstValue.reviewPayload, BillImportReview::class.java)
            val card = review.cards.single()

            assertThat(card.transactions).hasSize(2)
            assertThat(card.transactions.map { it.creditDebitType }).containsExactly(
                CreditDebitType.DEBIT,
                CreditDebitType.CREDIT,
            )
            assertThat(card.computedTotal).isEqualByComparingTo(BigDecimal("48.85"))
        }

        @Test
        fun `submit unsupported CSV layout returns unsupported format error`() {
            val fileBytes =
                """
                Data,Descricao,Valor
                01/03/2026,Compra,45.90
                """.trimIndent().toByteArray(Charsets.UTF_8)

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)

            val ex =
                assertThrows<BusinessRuleException> {
                    service.submitBillImport(
                        accountId = accountId,
                        userId = userId,
                        importType = ImportDocumentType.CREDIT_CARD_BILL,
                        financialAccountId = null,
                        fileName = "unknown.csv",
                        fileBytes = fileBytes,
                    )
                }

            assertThat(ex.errorCode).isEqualTo(BankingErrorCode.IMPORT_FILE_FORMAT_NOT_SUPPORTED)
            verify(repository, never()).save(any())
        }

        @Test
        fun `submit tabular XLSX bill creates awaiting review job`() {
            val fileBytes = buildTabularWorkbookBytes()
            val savedJobId = UUID.randomUUID()

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)
            whenever(repository.save(any())).thenAnswer {
                val job = it.getArgument<BillImportJob>(0)
                job.copy(id = savedJobId)
            }

            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.xlsx",
                    fileBytes = fileBytes,
                )

            assertThat(result.id).isEqualTo(savedJobId)
            assertThat(result.status).isEqualTo(BillImportJobStatus.AWAITING_REVIEW)
            verify(submittedProducer, never()).publish(any())
            verify(billAssetStorage, never()).uploadPdf(any(), any(), any())

            val savedJobCaptor = argumentCaptor<BillImportJob>()
            verify(repository).save(savedJobCaptor.capture())
            val review = objectMapper.readValue(savedJobCaptor.firstValue.reviewPayload, BillImportReview::class.java)
            assertThat(review.cards).hasSize(1)
            assertThat(review.cards.single().reviewCardId).isEqualTo("card-1")
            assertThat(review.cards.single().transactions).hasSize(2)
            assertThat(review.reviewMode).isEqualTo(BillImportReviewMode.CARD_BUCKET_MATCHING)
        }

        @Test
        fun `submit unsupported XLSX bill publishes assistant request and enters llm processing`() {
            val fileBytes = buildUnsupportedWorkbookBytes()
            val savedJobId = UUID.randomUUID()

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)
            whenever(repository.save(any())).thenAnswer {
                val job = it.getArgument<BillImportJob>(0)
                job.copy(id = savedJobId)
            }

            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.xlsx",
                    fileBytes = fileBytes,
                )

            assertThat(result.id).isEqualTo(savedJobId)
            assertThat(result.status).isEqualTo(BillImportJobStatus.LLM_PROCESSING)
            assertThat(result.extractedText).contains("Cartao", "Compra", "Valor")
            verify(submittedProducer, never()).publish(any())
            verify(billAssetStorage, never()).uploadPdf(any(), any(), any())

            val savedJobCaptor = argumentCaptor<BillImportJob>()
            verify(repository).save(savedJobCaptor.capture())
            assertThat(savedJobCaptor.firstValue.status).isEqualTo(BillImportJobStatus.LLM_PROCESSING)
            assertThat(savedJobCaptor.firstValue.reviewPayload).isNull()
            assertThat(savedJobCaptor.firstValue.extractedText).contains("Loja Exemplo")

            val eventCaptor = argumentCaptor<AssistantBankingReaderRequestedEvent>()
            verify(assistantRequestProducer).publish(eventCaptor.capture())
            val event = eventCaptor.firstValue
            assertThat(event.job_id).isEqualTo(savedJobId)
            assertThat(event.document_type).isEqualTo("CREDIT_CARD_BILL")
            assertThat(event.file_type).isEqualTo("XLSX")
            assertThat(event.account_type).isEqualTo("CREDIT")
            assertThat(event.source_topic).isEqualTo("banking.bill-import.excel-extracted")
            assertThat(event.extracted_text).contains("Loja Exemplo")
            assertThat(event.callback_context.account_id).isEqualTo(accountId)
            assertThat(event.callback_context.user_id).isEqualTo(userId)
            assertThat(event.extraction_metadata["ingest_source"]).isEqualTo("EXCEL")
        }

        @Test
        fun `submit OFX bill without card reference enters transaction assignment mode`() {
            val fileBytes =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <OFX>
                  <SIGNONMSGSRSV1>
                    <SONRS>
                      <STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
                      <DTSERVER>20240201120000</DTSERVER>
                      <LANGUAGE>POR</LANGUAGE>
                    </SONRS>
                  </SIGNONMSGSRSV1>
                  <CREDITCARDMSGSRSV1>
                    <CCSTMTTRNRS>
                      <TRNUID>1</TRNUID>
                      <STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
                      <CCSTMTRS>
                        <CURDEF>BRL</CURDEF>
                        <BANKTRANLIST>
                          <DTSTART>20240101000000</DTSTART>
                          <DTEND>20240131235959</DTEND>
                          <STMTTRN>
                            <TRNTYPE>DEBIT</TRNTYPE>
                            <DTPOSTED>20240120104500</DTPOSTED>
                            <TRNAMT>-42.99</TRNAMT>
                            <FITID>cc-xml-fitid-001</FITID>
                            <NAME>Online Store</NAME>
                            <MEMO>Purchase #123</MEMO>
                          </STMTTRN>
                        </BANKTRANLIST>
                      </CCSTMTRS>
                    </CCSTMTTRNRS>
                  </CREDITCARDMSGSRSV1>
                </OFX>
                """.trimIndent().toByteArray()
            val savedJobId = UUID.randomUUID()

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)
            whenever(repository.save(any())).thenAnswer {
                val job = it.getArgument<BillImportJob>(0)
                job.copy(id = savedJobId)
            }

            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.ofx",
                    fileBytes = fileBytes,
                )

            assertThat(result.id).isEqualTo(savedJobId)
            assertThat(result.status).isEqualTo(BillImportJobStatus.AWAITING_REVIEW)
            verify(submittedProducer, never()).publish(any())
            verify(billAssetStorage, never()).uploadPdf(any(), any(), any())

            val savedJobCaptor = argumentCaptor<BillImportJob>()
            verify(repository).save(savedJobCaptor.capture())
            val review = objectMapper.readValue(savedJobCaptor.firstValue.reviewPayload, BillImportReview::class.java)
            assertThat(review.reviewMode).isEqualTo(BillImportReviewMode.TX_CARD_ASSIGNMENT)
            assertThat(review.cards).hasSize(1)
            assertThat(review.cards.single().lastFourDigits).isNull()
            assertThat(review.cards.single().closingDate).isEqualTo(LocalDate.of(2024, 1, 31))
            assertThat(review.cards.single().transactions).hasSize(1)
        }

        @Test
        fun `submit OFX bill enriches installment and payment heuristics`() {
            val fileBytes =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <OFX>
                  <SIGNONMSGSRSV1>
                    <SONRS>
                      <STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
                      <DTSERVER>20240201120000</DTSERVER>
                      <LANGUAGE>POR</LANGUAGE>
                    </SONRS>
                  </SIGNONMSGSRSV1>
                  <CREDITCARDMSGSRSV1>
                    <CCSTMTTRNRS>
                      <TRNUID>1</TRNUID>
                      <STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
                      <CCSTMTRS>
                        <CURDEF>BRL</CURDEF>
                        <BANKTRANLIST>
                          <DTSTART>20240101000000</DTSTART>
                          <DTEND>20240131235959</DTEND>
                          <STMTTRN>
                            <TRNTYPE>DEBIT</TRNTYPE>
                            <DTPOSTED>20240120104500</DTPOSTED>
                            <TRNAMT>-42.99</TRNAMT>
                            <FITID>cc-xml-fitid-001</FITID>
                            <NAME>Hdi Seguros Sa 04/12</NAME>
                            <MEMO>Seguro</MEMO>
                          </STMTTRN>
                          <STMTTRN>
                            <TRNTYPE>CREDIT</TRNTYPE>
                            <DTPOSTED>20240121104500</DTPOSTED>
                            <TRNAMT>42.99</TRNAMT>
                            <FITID>cc-xml-fitid-002</FITID>
                            <NAME>Pagamento recebido</NAME>
                            <MEMO>Liquidacao</MEMO>
                          </STMTTRN>
                        </BANKTRANLIST>
                      </CCSTMTRS>
                    </CCSTMTTRNRS>
                  </CREDITCARDMSGSRSV1>
                </OFX>
                """.trimIndent().toByteArray()
            val savedJobId = UUID.randomUUID()

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)
            whenever(repository.save(any())).thenAnswer {
                val job = it.getArgument<BillImportJob>(0)
                job.copy(id = savedJobId)
            }

            service.submitBillImport(
                accountId = accountId,
                userId = userId,
                importType = ImportDocumentType.CREDIT_CARD_BILL,
                financialAccountId = null,
                fileName = "fatura.ofx",
                fileBytes = fileBytes,
            )

            val savedJobCaptor = argumentCaptor<BillImportJob>()
            verify(repository).save(savedJobCaptor.capture())
            val review = objectMapper.readValue(savedJobCaptor.firstValue.reviewPayload, BillImportReview::class.java)
            val firstTx = review.cards.single().transactions[0]
            val secondTx = review.cards.single().transactions[1]

            assertThat(firstTx.installment).isEqualTo(InstallmentInfo(current = 4, total = 12))
            assertThat(firstTx.isBillPayment).isFalse()
            assertThat(secondTx.isBillPayment).isTrue()
        }

        @Test
        fun `submit OFX bill does not treat bare ACCTID as card last four digits`() {
            val fileBytes =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <OFX>
                  <SIGNONMSGSRSV1>
                    <SONRS>
                      <STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
                      <DTSERVER>20240201120000</DTSERVER>
                      <LANGUAGE>POR</LANGUAGE>
                    </SONRS>
                  </SIGNONMSGSRSV1>
                  <CREDITCARDMSGSRSV1>
                    <CCSTMTTRNRS>
                      <TRNUID>1</TRNUID>
                      <STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
                      <CCSTMTRS>
                        <CURDEF>BRL</CURDEF>
                        <CCACCTFROM><ACCTID>1181</ACCTID></CCACCTFROM>
                        <BANKTRANLIST>
                          <DTSTART>20240101000000</DTSTART>
                          <DTEND>20240131235959</DTEND>
                          <STMTTRN>
                            <TRNTYPE>DEBIT</TRNTYPE>
                            <DTPOSTED>20240120104500</DTPOSTED>
                            <TRNAMT>-42.99</TRNAMT>
                            <FITID>cc-xml-fitid-001</FITID>
                            <NAME>Online Store</NAME>
                            <MEMO>Purchase #123</MEMO>
                          </STMTTRN>
                        </BANKTRANLIST>
                      </CCSTMTRS>
                    </CCSTMTTRNRS>
                  </CREDITCARDMSGSRSV1>
                </OFX>
                """.trimIndent().toByteArray()
            val savedJobId = UUID.randomUUID()

            whenever(repository.findByAccountIdAndFileHash(eq(accountId), any())).thenReturn(null)
            whenever(repository.save(any())).thenAnswer {
                val job = it.getArgument<BillImportJob>(0)
                job.copy(id = savedJobId)
            }

            val result =
                service.submitBillImport(
                    accountId = accountId,
                    userId = userId,
                    importType = ImportDocumentType.CREDIT_CARD_BILL,
                    financialAccountId = null,
                    fileName = "fatura.ofx",
                    fileBytes = fileBytes,
                )

            assertThat(result.id).isEqualTo(savedJobId)
            val savedJobCaptor = argumentCaptor<BillImportJob>()
            verify(repository).save(savedJobCaptor.capture())
            val review = objectMapper.readValue(savedJobCaptor.firstValue.reviewPayload, BillImportReview::class.java)
            assertThat(review.reviewMode).isEqualTo(BillImportReviewMode.TX_CARD_ASSIGNMENT)
            assertThat(review.cards.single().lastFourDigits).isNull()
        }
    }

    @Nested
    @DisplayName("projectReviewPayload")
    inner class ProjectReviewPayloadTests {
        @Test
        fun `projectReviewPayload applies existing rules during review projection`() {
            val categoryId = UUID.randomUUID()
            val payload =
                objectMapper.writeValueAsString(
                    BillImportReview(
                        bankName = "Nubank",
                        cards =
                            listOf(
                                BillImportReviewCard(
                                    reviewCardId = "card-1",
                                    lastFourDigits = "1234",
                                    transactions =
                                        listOf(
                                            BillImportReviewTransaction(
                                                date = LocalDate.of(2026, 1, 15),
                                                description = "IFOOD PEDIDO",
                                                amount = BigDecimal("42.90"),
                                                creditDebitType = CreditDebitType.DEBIT,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                )
            val rules =
                listOf(
                    TransactionRule(
                        id = UUID.randomUUID(),
                        accountId = accountId,
                        categoryId = categoryId,
                        pattern = "ifood",
                        patternType = PatternType.CONTAINS,
                        applicableTo = CategoryApplicableTo.EXPENSE,
                        priority = 50,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )

            whenever(categorizationService.preloadRules(accountId)).thenReturn(rules)
            whenever(
                categorizationService.suggestFromRules(
                    same(rules),
                    eq("IFOOD PEDIDO"),
                    eq(CreditDebitType.DEBIT),
                ),
            ).thenReturn(
                RuleSuggestion(
                    ruleId = rules.first().id!!,
                    categoryId = categoryId,
                    merchantId = null,
                ),
            )

            val result = service.projectReviewPayload(payload, accountId, userId)

            assertThat(result).isNotNull()
            assertThat(result!!.cards.single().transactions.single().categoryId).isEqualTo(categoryId)
            assertThat(result.cards.single().transactions.single().categorySource).isEqualTo(CategorySource.RULE)
            verify(categorizationService).preloadRules(accountId)
            verify(categorizationService).suggestFromRules(
                same(rules),
                eq("IFOOD PEDIDO"),
                eq(CreditDebitType.DEBIT),
            )
        }

        @Test
        fun `projectReviewPayload returns payload without suggestions when rule preload fails`() {
            val payload =
                objectMapper.writeValueAsString(
                    BillImportReview(
                        bankName = "Nubank",
                        cards =
                            listOf(
                                BillImportReviewCard(
                                    reviewCardId = "card-1",
                                    lastFourDigits = "1234",
                                    transactions =
                                        listOf(
                                            BillImportReviewTransaction(
                                                date = LocalDate.of(2026, 1, 15),
                                                description = "IFOOD PEDIDO",
                                                amount = BigDecimal("42.90"),
                                                creditDebitType = CreditDebitType.DEBIT,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                )
            whenever(categorizationService.preloadRules(accountId)).thenThrow(RuntimeException("rules unavailable"))

            val result = service.projectReviewPayload(payload, accountId, userId)

            assertThat(result).isNotNull()
            assertThat(result!!.cards.single().transactions.single().categoryId).isNull()
            assertThat(result.cards.single().transactions.single().categorySource).isNull()
            verify(categorizationService).preloadRules(accountId)
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
            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenAnswer {
                val account = it.getArgument<UUID>(0)
                val billId = it.getArgument<UUID>(1)
                "$account-$billId"
            }
            whenever(importFingerprintService.isDuplicateForBill(any(), any(), any())).thenReturn(false)
            doAnswer {
                val account = it.getArgument<FinancialAccount>(0)
                if (account.id == cardA) billA else billB
            }.whenever(creditCardBillService).resolveBillForReviewedCycle(any(), any(), any(), eq(TransactionType.CARD))
            doReturn(
                creditCardBill(billA, cardA, dueDate = LocalDate.of(2026, 2, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billA), eq(accountId))
            doReturn(
                creditCardBill(billB, cardB, dueDate = LocalDate.of(2026, 2, 18)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billB), eq(accountId))
            whenever(transactionRepository.save(any())).thenAnswer {
                val tx = it.getArgument<Transaction>(0)
                tx.copy(id = UUID.randomUUID())
            }

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
            verify(transactionRepository, times(10)).save(any())
            verify(creditCardBillService).recomputeBill(billA, accountId)
            verify(creditCardBillService).recomputeBill(billB, accountId)
            verify(snapshotComputationService).recomputeChain(eq(cardA), eq(accountId), eq(2026), eq(1))
            verify(snapshotComputationService).recomputeChain(eq(cardB), eq(accountId), eq(2026), eq(1))
        }

        @Test
        fun `confirm applies existing rules even when imported transaction starts uncategorized`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val categoryId = UUID.randomUUID()
            val rules =
                listOf(
                    TransactionRule(
                        id = UUID.randomUUID(),
                        accountId = accountId,
                        categoryId = categoryId,
                        pattern = "ifood",
                        patternType = PatternType.CONTAINS,
                        applicableTo = CategoryApplicableTo.EXPENSE,
                        priority = 50,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )
            val job = awaitingReviewJob(jobId)
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
                                            date = LocalDate.of(2026, 1, 15),
                                            description = "IFOOD PEDIDO",
                                            amount = BigDecimal("42.90"),
                                            creditDebitType = CreditDebitType.DEBIT,
                                            include = true,
                                        ),
                                    ),
                            ),
                        ),
                )

            whenever(repository.findById(jobId, accountId)).thenReturn(job)
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
                listOf(cardAccount(cardA, "1234")),
            )
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(categorizationService.preloadRules(accountId)).thenReturn(rules)
            whenever(
                categorizationService.suggestFromRulesRecordingMatch(
                    same(rules),
                    eq("IFOOD PEDIDO"),
                    eq(CreditDebitType.DEBIT),
                ),
            ).thenReturn(
                RuleSuggestion(
                    ruleId = rules.first().id!!,
                    categoryId = categoryId,
                    merchantId = null,
                ),
            )
            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicateForBill(any(), any(), any())).thenReturn(false)
            doReturn(billA).whenever(creditCardBillService).resolveBillForReviewedCycle(any(), any(), any(), any())
            doReturn(
                creditCardBill(billA, cardA, dueDate = LocalDate.of(2026, 2, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billA), eq(accountId))
            val savedCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.save(savedCaptor.capture())).thenAnswer {
                it.getArgument<Transaction>(0).copy(id = UUID.randomUUID())
            }

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
            assertThat(savedCaptor.firstValue.categoryId).isEqualTo(categoryId)
            assertThat(savedCaptor.firstValue.categorySource).isEqualTo(CategorySource.RULE)
            verify(categorizationService).preloadRules(accountId)
            verify(categorizationService).suggestFromRulesRecordingMatch(
                same(rules),
                eq("IFOOD PEDIDO"),
                eq(CreditDebitType.DEBIT),
            )
            verify(categorizationService, never()).learnFromManualAssignment(
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            )
        }

        @Test
        fun `confirm continues without categorization when rule preload fails`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
                                            date = LocalDate.of(2026, 1, 15),
                                            description = "IFOOD PEDIDO",
                                            amount = BigDecimal("42.90"),
                                            creditDebitType = CreditDebitType.DEBIT,
                                            include = true,
                                        ),
                                    ),
                            ),
                        ),
                )

            whenever(repository.findById(jobId, accountId)).thenReturn(job)
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
                listOf(cardAccount(cardA, "1234")),
            )
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(categorizationService.preloadRules(accountId)).thenThrow(RuntimeException("rules unavailable"))
            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicateForBill(any(), any(), any())).thenReturn(false)
            doReturn(billA).whenever(creditCardBillService).resolveBillForReviewedCycle(any(), any(), any(), any())
            doReturn(
                creditCardBill(billA, cardA, dueDate = LocalDate.of(2026, 2, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billA), eq(accountId))
            val savedCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.save(savedCaptor.capture())).thenAnswer {
                it.getArgument<Transaction>(0).copy(id = UUID.randomUUID())
            }

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
            assertThat(savedCaptor.firstValue.categoryId).isNull()
            assertThat(savedCaptor.firstValue.categorySource).isNull()
            verify(categorizationService).preloadRules(accountId)
            verify(categorizationService).suggestFromRulesRecordingMatch(
                eq(emptyList()),
                eq("IFOOD PEDIDO"),
                eq(CreditDebitType.DEBIT),
            )
        }

        @Test
        fun `confirm skips transactions marked include false`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
                                            date = LocalDate.of(2026, 1, 15),
                                            description = "MERCADO",
                                            amount = BigDecimal("45.90"),
                                            creditDebitType = CreditDebitType.DEBIT,
                                            include = false,
                                        ),
                                        ConfirmBillReviewTransactionCommand(
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
            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicateForBill(any(), any(), any())).thenReturn(false)
            doReturn(billA).whenever(creditCardBillService).resolveBillForReviewedCycle(any(), any(), any(), any())
            doReturn(
                creditCardBill(billA, cardA, dueDate = LocalDate.of(2026, 2, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billA), eq(accountId))
            whenever(
                transactionRepository.save(any()),
            ).thenAnswer { it.getArgument<Transaction>(0).copy(id = UUID.randomUUID()) }

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
            verify(transactionRepository, times(1)).save(any())
        }

        @Test
        fun `confirm applies manual category and triggers implicit learning`() {
            whenever(userRuleCreationModeService.get(userId)).thenReturn(RuleCreationMode.ALWAYS)
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val categoryId = UUID.randomUUID()
            val merchantId = UUID.randomUUID()
            val preloadedRules =
                listOf(
                    TransactionRule(
                        id = UUID.randomUUID(),
                        accountId = accountId,
                        merchantId = merchantId,
                        pattern = "netflix",
                        patternType = PatternType.CONTAINS,
                        applicableTo = CategoryApplicableTo.ANY,
                        priority = 40,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )

            val job = awaitingReviewJob(jobId)
            val tx =
                ConfirmBillReviewTransactionCommand(
                    date = LocalDate.of(2026, 1, 15),
                    description = "NETFLIX.COM*BR",
                    amount = BigDecimal("45.90"),
                    creditDebitType = CreditDebitType.DEBIT,
                    include = true,
                    categoryId = categoryId,
                    categorySource = CategorySource.MANUAL,
                )

            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions = listOf(tx),
                            ),
                        ),
                )

            whenever(repository.findById(jobId, accountId)).thenReturn(job)
            whenever(
                financialAccountRepository.findByAccountId(accountId),
            ).thenReturn(listOf(cardAccount(cardA, "1234")))
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(categorizationService.preloadRules(accountId)).thenReturn(preloadedRules)
            whenever(
                categorizationService.suggestFromRules(
                    same(preloadedRules),
                    eq(tx.description),
                    eq(tx.creditDebitType),
                ),
            ).thenReturn(
                RuleSuggestion(
                    ruleId = preloadedRules.first().id!!,
                    categoryId = null,
                    merchantId = merchantId,
                ),
            )

            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicateForBill(any(), any(), any())).thenReturn(false)

            doReturn(
                billA,
            ).whenever(creditCardBillService).resolveBillForReviewedCycle(any(), any(), any(), eq(TransactionType.CARD))
            doReturn(
                creditCardBill(billA, cardA, dueDate = LocalDate.of(2026, 2, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billA), eq(accountId))

            whenever(
                transactionRepository.save(any()),
            ).thenAnswer { it.getArgument<Transaction>(0).copy(id = UUID.randomUUID()) }

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)

            val txCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository, times(1)).save(txCaptor.capture())
            assertThat(txCaptor.firstValue.categoryId).isEqualTo(categoryId)
            assertThat(txCaptor.firstValue.categorySource).isEqualTo(CategorySource.MANUAL)
            assertThat(txCaptor.firstValue.merchantId).isEqualTo(merchantId)

            verify(categorizationService).learnFromManualAssignment(
                eq(accountId),
                eq(tx.description),
                eq(categoryId),
                eq(merchantId),
                anyOrNull(),
                anyOrNull(),
                eq(false),
            )
        }

        @Test
        fun `confirm with ALWAYS_PROMPT does not auto-create implicit rule from manual category`() {
            whenever(userRuleCreationModeService.get(userId)).thenReturn(RuleCreationMode.ALWAYS_PROMPT)
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val categoryId = UUID.randomUUID()
            val merchantId = UUID.randomUUID()
            val preloadedRules =
                listOf(
                    TransactionRule(
                        id = UUID.randomUUID(),
                        accountId = accountId,
                        merchantId = merchantId,
                        pattern = "netflix",
                        patternType = PatternType.CONTAINS,
                        applicableTo = CategoryApplicableTo.ANY,
                        priority = 40,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )

            val job = awaitingReviewJob(jobId)
            val tx =
                ConfirmBillReviewTransactionCommand(
                    date = LocalDate.of(2026, 1, 15),
                    description = "NETFLIX.COM*BR",
                    amount = BigDecimal("45.90"),
                    creditDebitType = CreditDebitType.DEBIT,
                    include = true,
                    categoryId = categoryId,
                    categorySource = CategorySource.MANUAL,
                )

            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions = listOf(tx),
                            ),
                        ),
                )

            whenever(repository.findById(jobId, accountId)).thenReturn(job)
            whenever(
                financialAccountRepository.findByAccountId(accountId),
            ).thenReturn(listOf(cardAccount(cardA, "1234")))
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(categorizationService.preloadRules(accountId)).thenReturn(preloadedRules)
            whenever(
                categorizationService.suggestFromRules(
                    same(preloadedRules),
                    eq(tx.description),
                    eq(tx.creditDebitType),
                ),
            ).thenReturn(
                RuleSuggestion(
                    ruleId = preloadedRules.first().id!!,
                    categoryId = null,
                    merchantId = merchantId,
                ),
            )
            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicateForBill(any(), any(), any())).thenReturn(false)
            doReturn(
                billA,
            ).whenever(creditCardBillService).resolveBillForReviewedCycle(any(), any(), any(), eq(TransactionType.CARD))
            doReturn(
                creditCardBill(billA, cardA, dueDate = LocalDate.of(2026, 2, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billA), eq(accountId))
            whenever(
                transactionRepository.save(any()),
            ).thenAnswer { it.getArgument<Transaction>(0).copy(id = UUID.randomUUID()) }

            service.confirmBillImport(accountId, userId, command)

            verify(categorizationService, never()).learnFromManualAssignment(
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            )
        }

        @Test
        fun `confirm silently skips duplicate fingerprint`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
                                            date = LocalDate.of(2026, 1, 15),
                                            description = "MERCADO",
                                            amount = BigDecimal("45.90"),
                                            creditDebitType = CreditDebitType.DEBIT,
                                            include = true,
                                        ),
                                        ConfirmBillReviewTransactionCommand(
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
            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenReturn("fp-1", "fp-2")
            whenever(importFingerprintService.isDuplicateForBill(cardA, billA, "fp-1")).thenReturn(true)
            whenever(importFingerprintService.isDuplicateForBill(cardA, billA, "fp-2")).thenReturn(false)
            doReturn(billA).whenever(creditCardBillService).resolveBillForReviewedCycle(any(), any(), any(), any())
            doReturn(
                creditCardBill(billA, cardA, dueDate = LocalDate.of(2026, 2, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billA), eq(accountId))
            whenever(
                transactionRepository.save(any()),
            ).thenAnswer { it.getArgument<Transaction>(0).copy(id = UUID.randomUUID()) }

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
            verify(transactionRepository, times(1)).save(any())
        }

        @Test
        fun `confirm allows same amount and description within the same bill`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billJan = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
                                            date = LocalDate.of(2026, 1, 20),
                                            description = "STREAMING SERVICE",
                                            amount = BigDecimal("39.90"),
                                            creditDebitType = CreditDebitType.DEBIT,
                                            include = true,
                                        ),
                                        ConfirmBillReviewTransactionCommand(
                                            date = LocalDate.of(2026, 2, 20),
                                            description = "STREAMING SERVICE",
                                            amount = BigDecimal("39.90"),
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
            doReturn(billJan).whenever(creditCardBillService).resolveBillForReviewedCycle(any(), any(), any(), any())
            doReturn(
                creditCardBill(billJan, cardA, dueDate = LocalDate.of(2026, 2, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billJan), eq(accountId))
            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenAnswer {
                val billId = it.getArgument<UUID>(1)
                "fp-$billId"
            }
            whenever(importFingerprintService.isDuplicateForBill(any(), any(), any())).thenReturn(false)
            whenever(transactionRepository.save(any())).thenAnswer {
                it.getArgument<Transaction>(0).copy(id = UUID.randomUUID())
            }

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
            verify(transactionRepository, times(2)).save(any())
            verify(importFingerprintService, times(2)).isDuplicateForBill(eq(cardA), eq(billJan), eq("fp-$billJan"))
        }

        @Test
        fun `confirm persists installment metadata for card transactions`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
                                            date = LocalDate.of(2026, 2, 19),
                                            description = "Playcenter",
                                            amount = BigDecimal("41.70"),
                                            creditDebitType = CreditDebitType.DEBIT,
                                            include = true,
                                            installment = InstallmentInfo(current = 1, total = 6),
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
            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicateForBill(any(), any(), any())).thenReturn(false)
            doReturn(billA).whenever(creditCardBillService).resolveBillForReviewedCycle(any(), any(), any(), any())
            doReturn(
                creditCardBill(billA, cardA, dueDate = LocalDate.of(2026, 3, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billA), eq(accountId))
            whenever(
                transactionRepository.save(any()),
            ).thenAnswer { it.getArgument<Transaction>(0).copy(id = UUID.randomUUID()) }

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
            val txCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository).save(txCaptor.capture())
            assertThat(txCaptor.firstValue.installmentNumber).isEqualTo(1)
            assertThat(txCaptor.firstValue.totalInstallments).isEqualTo(6)
        }

        @Test
        fun `confirm sanitizes future installment date using bill cycle context`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val job = awaitingReviewJobWithDueDate(jobId, lastFour = "1234", dueDate = LocalDate.of(2026, 3, 15))
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
                                            date = LocalDate.of(2026, 9, 30),
                                            description = "Assinatura 06/12",
                                            amount = BigDecimal("79.90"),
                                            creditDebitType = CreditDebitType.DEBIT,
                                            include = true,
                                            installment = InstallmentInfo(current = 6, total = 12),
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
            doReturn(billA).whenever(creditCardBillService).resolveBillForReviewedCycle(any(), any(), any(), any())
            doReturn(
                creditCardBill(billA, cardA, dueDate = LocalDate.of(2026, 3, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billA), eq(accountId))
            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicateForBill(any(), any(), any())).thenReturn(false)
            whenever(transactionRepository.save(any())).thenAnswer {
                it.getArgument<Transaction>(0).copy(id = UUID.randomUUID())
            }

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
            val txCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository).save(txCaptor.capture())
            assertThat(txCaptor.firstValue.date).isEqualTo(LocalDate.of(2025, 9, 30))
        }

        @Test
        fun `confirm sanitizes implausibly recent installment month using cycle context`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val job = awaitingReviewJobWithDueDate(jobId, lastFour = "1234", dueDate = LocalDate.of(2026, 3, 15))
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
                                            date = LocalDate.of(2026, 2, 1),
                                            description = "Cia Ferrasa De Empreen",
                                            amount = BigDecimal("59.60"),
                                            creditDebitType = CreditDebitType.DEBIT,
                                            include = true,
                                            installment = InstallmentInfo(current = 5, total = 5),
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
            doReturn(billA).whenever(creditCardBillService).resolveBillForReviewedCycle(any(), any(), any(), any())
            doReturn(
                creditCardBill(billA, cardA, dueDate = LocalDate.of(2026, 3, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billA), eq(accountId))
            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicateForBill(any(), any(), any())).thenReturn(false)
            whenever(transactionRepository.save(any())).thenAnswer {
                it.getArgument<Transaction>(0).copy(id = UUID.randomUUID())
            }

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
            val txCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository).save(txCaptor.capture())
            assertThat(txCaptor.firstValue.date).isEqualTo(LocalDate.of(2025, 10, 1))
        }

        @Test
        fun `confirm derives due date from selected card account when review payload has only closing date`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val billA = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
                                            date = LocalDate.of(2026, 2, 5),
                                            description = "MERCADO",
                                            amount = BigDecimal("45.90"),
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
            doReturn(billA).whenever(creditCardBillService).resolveBillForReviewedCycle(
                any(),
                eq(LocalDate.of(2026, 2, 7)),
                eq(LocalDate.of(2026, 2, 15)),
                eq(TransactionType.CARD),
            )
            doReturn(
                creditCardBill(billA, cardA, dueDate = LocalDate.of(2026, 2, 15)),
            ).whenever(creditCardBillService).requireBillForAccount(eq(billA), eq(accountId))
            whenever(importFingerprintService.computeForBill(any(), any(), any(), any())).thenReturn("fp-1")
            whenever(importFingerprintService.isDuplicateForBill(any(), any(), any())).thenReturn(false)
            whenever(transactionRepository.save(any())).thenAnswer {
                it.getArgument<Transaction>(0).copy(id = UUID.randomUUID())
            }

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
            verify(creditCardBillService).resolveBillForReviewedCycle(
                any(),
                eq(LocalDate.of(2026, 2, 7)),
                eq(LocalDate.of(2026, 2, 15)),
                eq(TransactionType.CARD),
            )
        }

        @Test
        fun `confirm without closing date anywhere returns validation error`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val job = awaitingReviewJobWithoutCycle(jobId)
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
                                            date = LocalDate.of(2026, 2, 5),
                                            description = "MERCADO",
                                            amount = BigDecimal("45.90"),
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

            val ex =
                assertThrows<ValidationException> {
                    service.confirmBillImport(accountId, userId, command)
                }

            assertThat(ex.field).isEqualTo("closingDate")
            verify(repository, never()).update(any())
            verify(transactionRepository, never()).save(any())
        }

        @Test
        fun `confirm bill payment without linked bank saves TRANSFER CREDIT on card only`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
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

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
            val txCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository, times(1)).save(txCaptor.capture())
            val savedTx = txCaptor.firstValue
            assertThat(savedTx.type).isEqualTo(TransactionType.TRANSFER)
            assertThat(savedTx.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(savedTx.billId).isNull()
            verify(transactionRepository, never()).update(any())
            verify(creditCardBillService, never()).resolveBillForReviewedCycle(any(), any(), any(), any())
        }

        @Test
        fun `confirm bill payment with linked bank creates paired TRANSFER on both accounts`() {
            val jobId = UUID.randomUUID()
            val cardA = UUID.randomUUID()
            val linkedBankId = UUID.randomUUID()
            val job = awaitingReviewJob(jobId)
            val command =
                ConfirmBillImportCommand(
                    jobId = jobId,
                    cards =
                        listOf(
                            ConfirmBillReviewCardCommand(
                                reviewCardId = "card-1",
                                financialAccountId = cardA,
                                transactions =
                                    listOf(
                                        ConfirmBillReviewTransactionCommand(
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

            val result = service.confirmBillImport(accountId, userId, command)

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
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
            verify(creditCardBillService, never()).resolveBillForReviewedCycle(any(), any(), any(), any())
        }

        @Test
        fun `confirm with wrong status returns validation error`() {
            val jobId = UUID.randomUUID()
            whenever(repository.findById(jobId, accountId)).thenReturn(
                BillImportJob(
                    id = jobId,
                    financialAccountId = null,
                    accountId = accountId,
                    createdByUserId = userId,
                    status = BillImportJobStatus.PENDING,
                    assetId = UUID.randomUUID(),
                    fileName = "fatura.pdf",
                    fileHash = "hash",
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val ex =
                assertThrows<ValidationException> {
                    service.confirmBillImport(
                        accountId,
                        userId,
                        ConfirmBillImportCommand(jobId = jobId, cards = emptyList()),
                    )
                }

            assertThat(ex.field).isEqualTo("jobId")
            verify(transactionRepository, never()).save(any())
        }

        @Test
        fun `confirm on DONE is idempotent and returns existing job`() {
            val jobId = UUID.randomUUID()
            val doneJob =
                BillImportJob(
                    id = jobId,
                    financialAccountId = null,
                    accountId = accountId,
                    createdByUserId = userId,
                    status = BillImportJobStatus.DONE,
                    assetId = UUID.randomUUID(),
                    fileName = "fatura.pdf",
                    fileHash = "hash",
                    completedAt = now,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(repository.findById(jobId, accountId)).thenReturn(doneJob)

            val result =
                service.confirmBillImport(
                    accountId,
                    userId,
                    ConfirmBillImportCommand(jobId = jobId, cards = emptyList()),
                )

            assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
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

            val updatedCaptor = argumentCaptor<BillImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val payload =
                objectMapper.readValue(
                    updatedCaptor.firstValue.reviewPayload,
                    BillImportReview::class.java,
                )
            assertThat(payload.cards).hasSize(1)
            assertThat(payload.cards[0].matchedFinancialAccountId).isEqualTo(matchedAccountId)
            assertThat(payload.cards[0].matchAmbiguous).isFalse()
        }

        @Test
        fun `markAwaitingReview propagates top level bill dates into review cards`() {
            val jobId = UUID.randomUUID()
            whenever(repository.findById(jobId, accountId)).thenReturn(baseJob(jobId))
            whenever(
                financialAccountRepository.findCardAccountsByLastFourDigits(accountId, "1234"),
            ).thenReturn(emptyList())
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            service.markAwaitingReview(
                jobId,
                accountId,
                mapOf(
                    "bank_name" to "Nubank",
                    "closing_date" to "2025-10-07",
                    "due_date" to "2025-10-14",
                    "cards" to
                        listOf(
                            mapOf(
                                "last_four_digits" to "1234",
                                "declared_total" to 100.0,
                                "computed_total" to 100.0,
                                "total_matches_declared" to true,
                                "transactions" to emptyList<Map<String, Any?>>(),
                            ),
                        ),
                ),
            )

            val updatedCaptor = argumentCaptor<BillImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val payload = objectMapper.readValue(updatedCaptor.firstValue.reviewPayload, BillImportReview::class.java)
            assertThat(payload.cards).hasSize(1)
            assertThat(payload.cards[0].closingDate).isEqualTo(LocalDate.of(2025, 10, 7))
            assertThat(payload.cards[0].dueDate).isEqualTo(LocalDate.of(2025, 10, 14))
        }

        @Test
        fun `markAwaitingReview builds review from assistant metadata and stores assistant response`() {
            val jobId = UUID.randomUUID()
            val extractedText =
                """
                JOAO SILVA - 5428 XXXX XXXX 1234
                21/01 PAGAMENTO DE FATURA-INTERNET -99,10
                1 24/01 HORTIFRUTI UNIAO 75,77
                """.trimIndent()
            whenever(
                repository.findById(jobId, accountId),
            ).thenReturn(baseJob(jobId).copy(extractedText = extractedText))
            whenever(
                financialAccountRepository.findCardAccountsByLastFourDigits(accountId, "1234"),
            ).thenReturn(emptyList())
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            val assistantEvent =
                mapOf(
                    "schema_version" to "1.0",
                    "llm_output" to assistantMetaPayload("JOAO SILVA - 5428 XXXX XXXX 1234", "1234"),
                    "parse_metadata" to mapOf("reader_id" to "assistant-banking-bill-reader-v2"),
                )

            service.markAwaitingReview(
                jobId = jobId,
                accountId = accountId,
                llmOutput = assistantMetaPayload("JOAO SILVA - 5428 XXXX XXXX 1234", "1234"),
                assistantResponse = assistantEvent,
            )

            val updatedCaptor = argumentCaptor<BillImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val updated = updatedCaptor.firstValue
            val payload = objectMapper.readValue(updated.reviewPayload, BillImportReview::class.java)
            assertThat(payload.cards).hasSize(1)
            assertThat(payload.cards[0].lastFourDigits).isEqualTo("1234")
            assertThat(payload.cards[0].closingDate).isEqualTo(LocalDate.of(2026, 2, 12))
            assertThat(payload.cards[0].dueDate).isEqualTo(LocalDate.of(2026, 2, 22))
            assertThat(payload.cards[0].transactions).hasSize(2)
            assertThat(payload.cards[0].transactions[0].isBillPayment).isTrue()
            assertThat(updated.assistantResponse).contains("assistant-banking-bill-reader-v2")
            assertThat(updated.assistantResponse).contains("card_blocks")
        }

        @Test
        fun `markAwaitingReview treats missing card blocks and dot-mask inline pattern as transaction assignment`() {
            val jobId = UUID.randomUUID()
            val extractedText =
                """
                15 SET •••• 4416 Dm *Helphbomaxcom R$ 19,95
                18 SET •••• 1639 Hdi Seguros Sa 04/12 R$ 236,66
                30 SET IOF de "Cursor, Ai Powered Ide" R$ 3,88
                30 SET •••• 5954 Cursor, Ai Powered Ide R$ 110,72
                03 OUT •••• 1769 Netflix.Com R$ 44,90
                08 SET Pagamento em 08 SET −R$ 418,33
                """.trimIndent()
            whenever(
                repository.findById(jobId, accountId),
            ).thenReturn(baseJob(jobId).copy(extractedText = extractedText))
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            service.markAwaitingReview(
                jobId = jobId,
                accountId = accountId,
                llmOutput =
                    mapOf(
                        "bank_name" to "Nubank",
                        "closing_date" to "2025-10-07",
                        "due_date" to "2025-10-14",
                        "declared_total" to 416.11,
                        "transaction_pattern" to
                            mapOf(
                                "currency_symbols" to listOf("R$", "USD"),
                                "date_format" to "DD_MMM",
                                "decimal_separator" to "comma",
                                "installment_pattern" to """(\d{1,2})/(\d{1,2})""",
                                "last_four_pattern" to """\.{4}\s*(\d{4})""",
                            ),
                    ),
            )

            val updatedCaptor = argumentCaptor<BillImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val payload = objectMapper.readValue(updatedCaptor.firstValue.reviewPayload, BillImportReview::class.java)
            assertThat(payload.reviewMode).isEqualTo(BillImportReviewMode.CARD_BUCKET_MATCHING)
            assertThat(payload.cards).hasSize(5)
            assertThat(payload.cards.mapNotNull { it.lastFourDigits })
                .containsExactly("4416", "1639", "5954", "1769")
            assertThat(
                payload.cards.first { it.lastFourDigits == "1639" }.transactions.single().installment?.current,
            ).isEqualTo(4)
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

            val updatedCaptor = argumentCaptor<BillImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val payload =
                objectMapper.readValue(
                    updatedCaptor.firstValue.reviewPayload,
                    BillImportReview::class.java,
                )
            assertThat(payload.cards[0].matchedFinancialAccountId).isNull()
            assertThat(payload.cards[0].matchAmbiguous).isFalse()
            assertThat(payload.cards[0].matchCandidates).isEmpty()
        }

        @Test
        fun `markAwaitingReview sanitizes future installment year in review payload`() {
            val jobId = UUID.randomUUID()
            whenever(repository.findById(jobId, accountId)).thenReturn(baseJob(jobId))
            whenever(
                financialAccountRepository.findCardAccountsByLastFourDigits(accountId, "1234"),
            ).thenReturn(emptyList())
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            service.markAwaitingReview(jobId, accountId, llmPayloadWithFutureInstallmentDate("1234"))

            val updatedCaptor = argumentCaptor<BillImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val payload =
                objectMapper.readValue(
                    updatedCaptor.firstValue.reviewPayload,
                    BillImportReview::class.java,
                )
            assertThat(payload.cards[0].transactions).hasSize(1)
            assertThat(payload.cards[0].transactions[0].date).isEqualTo(LocalDate.of(2025, 9, 30))
        }

        @Test
        fun `markAwaitingReview sanitizes implausibly recent installment date in review payload`() {
            val jobId = UUID.randomUUID()
            whenever(repository.findById(jobId, accountId)).thenReturn(baseJob(jobId))
            whenever(
                financialAccountRepository.findCardAccountsByLastFourDigits(accountId, "1234"),
            ).thenReturn(emptyList())
            whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

            service.markAwaitingReview(jobId, accountId, llmPayloadWithRecentInstallmentDate("1234"))

            val updatedCaptor = argumentCaptor<BillImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val payload =
                objectMapper.readValue(
                    updatedCaptor.firstValue.reviewPayload,
                    BillImportReview::class.java,
                )
            assertThat(payload.cards[0].transactions).hasSize(1)
            assertThat(payload.cards[0].transactions[0].date).isEqualTo(LocalDate.of(2025, 10, 1))
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

            val updatedCaptor = argumentCaptor<BillImportJob>()
            verify(repository).update(updatedCaptor.capture())
            val payload =
                objectMapper.readValue(
                    updatedCaptor.firstValue.reviewPayload,
                    BillImportReview::class.java,
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

            val updatedCaptor = argumentCaptor<BillImportJob>()
            verify(repository).update(updatedCaptor.capture())
            assertThat(updatedCaptor.firstValue.status).isEqualTo(BillImportJobStatus.AWAITING_REVIEW)

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
                errorMessage =
                    "assistant_banking_reader_failed: SCHEMA_VALIDATION_FAILED - " +
                        "output did not match contract",
                errorDetails = details,
            )

        assertThat(result).isNotNull
        assertThat(result!!.status).isEqualTo(BillImportJobStatus.FAILED)
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

    private fun creditCardBill(
        id: UUID,
        financialAccountId: UUID,
        dueDate: LocalDate,
    ): CreditCardBill =
        CreditCardBill(
            id = id,
            financialAccountId = financialAccountId,
            accountId = accountId,
            closingDate = dueDate.minusDays(8),
            dueDate = dueDate,
            totalAmount = BigDecimal.ZERO,
            status = BillStatus.OPEN,
            createdAt = now,
            updatedAt = now,
        )

    private fun baseJob(jobId: UUID): BillImportJob =
        BillImportJob(
            id = jobId,
            financialAccountId = null,
            accountId = accountId,
            createdByUserId = userId,
            status = BillImportJobStatus.LLM_PROCESSING,
            assetId = UUID.randomUUID(),
            fileName = "fatura.pdf",
            fileHash = "hash",
            createdAt = now,
            updatedAt = now,
        )

    private fun awaitingReviewJob(jobId: UUID): BillImportJob {
        val payload =
            BillImportReview(
                bankName = "Nubank",
                statementPeriod = DateRange(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 2, 7)),
                cards =
                    listOf(
                        BillImportReviewCard(
                            reviewCardId = "card-1",
                            lastFourDigits = "1234",
                            transactions = emptyList(),
                        ),
                        BillImportReviewCard(
                            reviewCardId = "card-2",
                            lastFourDigits = "5678",
                            transactions = emptyList(),
                        ),
                    ),
            )
        return BillImportJob(
            id = jobId,
            financialAccountId = null,
            accountId = accountId,
            createdByUserId = userId,
            status = BillImportJobStatus.AWAITING_REVIEW,
            assetId = UUID.randomUUID(),
            fileName = "fatura.pdf",
            fileHash = "hash",
            reviewPayload = objectMapper.writeValueAsString(payload),
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun awaitingReviewJobWithDueDate(
        jobId: UUID,
        lastFour: String,
        dueDate: LocalDate,
    ): BillImportJob {
        val payload =
            BillImportReview(
                bankName = "Nubank",
                statementPeriod = DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)),
                cards =
                    listOf(
                        BillImportReviewCard(
                            reviewCardId = "card-1",
                            lastFourDigits = lastFour,
                            closingDate = dueDate.minusDays(8),
                            dueDate = dueDate,
                            transactions = emptyList(),
                        ),
                    ),
            )
        return BillImportJob(
            id = jobId,
            financialAccountId = null,
            accountId = accountId,
            createdByUserId = userId,
            status = BillImportJobStatus.AWAITING_REVIEW,
            assetId = UUID.randomUUID(),
            fileName = "fatura.pdf",
            fileHash = "hash",
            reviewPayload = objectMapper.writeValueAsString(payload),
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun awaitingReviewJobWithoutCycle(jobId: UUID): BillImportJob {
        val payload =
            BillImportReview(
                bankName = "Nubank",
                cards =
                    listOf(
                        BillImportReviewCard(
                            reviewCardId = "card-1",
                            lastFourDigits = null,
                            transactions = emptyList(),
                        ),
                    ),
            )
        return BillImportJob(
            id = jobId,
            financialAccountId = null,
            accountId = accountId,
            createdByUserId = userId,
            status = BillImportJobStatus.AWAITING_REVIEW,
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
    ): ConfirmBillImportCommand {
        val txsA =
            (1..5).map { idx ->
                ConfirmBillReviewTransactionCommand(
                    date = LocalDate.of(2026, 1, 10 + idx),
                    description = "Card A tx $idx",
                    amount = BigDecimal("10.0$idx"),
                    creditDebitType = CreditDebitType.DEBIT,
                    include = true,
                )
            }
        val txsB =
            (1..5).map { idx ->
                ConfirmBillReviewTransactionCommand(
                    date = LocalDate.of(2026, 1, 20 + idx),
                    description = "Card B tx $idx",
                    amount = BigDecimal("20.0$idx"),
                    creditDebitType = CreditDebitType.DEBIT,
                    include = true,
                )
            }
        return ConfirmBillImportCommand(
            jobId = jobId,
            cards =
                listOf(
                    ConfirmBillReviewCardCommand(
                        reviewCardId = "card-1",
                        financialAccountId = cardA,
                        transactions = txsA,
                    ),
                    ConfirmBillReviewCardCommand(
                        reviewCardId = "card-2",
                        financialAccountId = cardB,
                        transactions = txsB,
                    ),
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
        )

    private fun assistantMetaPayload(
        anchorStart: String,
        lastFour: String,
    ): Map<String, Any?> =
        mapOf(
            "bank_name" to "Santander",
            "closing_date" to "2026-02-12",
            "due_date" to "2026-02-22",
            "declared_total" to 151.25,
            "card_blocks" to
                listOf(
                    mapOf(
                        "last_four" to lastFour,
                        "declared_total" to null,
                        "anchor_start" to anchorStart,
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

    private fun llmPayloadWithFutureInstallmentDate(lastFour: String): Map<String, Any?> =
        mapOf(
            "bank_name" to "Nubank",
            "statement_period" to
                mapOf(
                    "from" to "2026-02-01",
                    "to" to "2026-02-28",
                ),
            "cards" to
                listOf(
                    mapOf(
                        "last_four_digits" to lastFour,
                        "closing_date" to "2026-03-07",
                        "due_date" to "2026-03-15",
                        "declared_total" to 79.90,
                        "computed_total" to 79.90,
                        "total_matches_declared" to true,
                        "transactions" to
                            listOf(
                                mapOf(
                                    "date" to "2026-09-30",
                                    "description" to "Assinatura 06/12",
                                    "amount" to 79.90,
                                    "credit_debit_type" to "DEBIT",
                                    "installment" to
                                        mapOf(
                                            "current" to 6,
                                            "total" to 12,
                                        ),
                                    "is_bill_payment" to false,
                                ),
                            ),
                    ),
                ),
        )

    private fun llmPayloadWithRecentInstallmentDate(lastFour: String): Map<String, Any?> =
        mapOf(
            "bank_name" to "Nubank",
            "statement_period" to
                mapOf(
                    "from" to "2026-02-01",
                    "to" to "2026-02-28",
                ),
            "cards" to
                listOf(
                    mapOf(
                        "last_four_digits" to lastFour,
                        "closing_date" to "2026-03-07",
                        "due_date" to "2026-03-15",
                        "declared_total" to 59.60,
                        "computed_total" to 59.60,
                        "total_matches_declared" to true,
                        "transactions" to
                            listOf(
                                mapOf(
                                    "date" to "2026-02-01",
                                    "description" to "Cia Ferrasa De Empreen",
                                    "amount" to 59.60,
                                    "credit_debit_type" to "DEBIT",
                                    "installment" to
                                        mapOf(
                                            "current" to 5,
                                            "total" to 5,
                                        ),
                                    "is_bill_payment" to false,
                                ),
                            ),
                    ),
                ),
        )

    private fun canonicalReviewOutput(): CanonicalReaderOutput =
        CanonicalReaderOutput(
            schemaId = CanonicalReaderOutputMapper.SCHEMA_ID,
            schemaVersion = CanonicalReaderOutputMapper.SCHEMA_VERSION,
            processorType = CanonicalProcessorType.DETERMINISTIC,
            readerId = "pdf-reader-nubank-credit-card-v1",
            confidence = 0.98,
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

    private fun loadFixture(path: String): ByteArray = javaClass.getResourceAsStream(path)!!.readBytes()

    private fun xpCsvWithHolderBytes(): ByteArray =
        """
        Data;Estabelecimento;Portador;Valor;Parcela
        01/03/2026;Mercado Extra;Joao Silva;R$ 100,00;1 de 3
        02/03/2026;Farmacia;Maria Souza;R$ 50,00;-
        15/02/2026;Posto;Joao Silva;R$ 20,00;-
        """.trimIndent().toByteArray(Charsets.UTF_8)

    private fun xpCsvWithoutHolderBytes(): ByteArray =
        """
        Data;Estabelecimento;Valor;Parcela
        01/03/2026;Mercado Extra;R$ 100,00;1 de 3
        02/03/2026;Farmacia;R$ 50,00;7 de 7
        """.trimIndent().toByteArray(Charsets.UTF_8)

    private fun xpCsvWithRefundBytes(): ByteArray =
        """
        Data;Estabelecimento;Valor;Parcela
        01/03/2026;Mercado Extra;R$ 100,00;-
        02/03/2026;Estorno Loja;R$ -51,15;-
        """.trimIndent().toByteArray(Charsets.UTF_8)

    private fun buildTabularWorkbookBytes(): ByteArray {
        val workbook: Workbook = XSSFWorkbook()
        workbook.use { wb ->
            val sheet = wb.createSheet("Extrato")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("Data")
                createCell(1).setCellValue("Descricao")
                createCell(2).setCellValue("Valor")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("03/02/2026")
                createCell(1).setCellValue("Mercado Extra")
                createCell(2).setCellValue("-45,90")
            }
            sheet.createRow(2).apply {
                createCell(0).setCellValue("05/02/2026")
                createCell(1).setCellValue("Farmacia Sao Joao")
                createCell(2).setCellValue("-32,10")
            }
            return workbookToBytes(wb)
        }
    }

    private fun buildUnsupportedWorkbookBytes(): ByteArray {
        val workbook: Workbook = XSSFWorkbook()
        workbook.use { wb ->
            val sheet = wb.createSheet("Fatura")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("Cartao")
                createCell(1).setCellValue("Compra")
                createCell(2).setCellValue("Valor")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("1234")
                createCell(1).setCellValue("Loja Exemplo")
                createCell(2).setCellValue("45,90")
            }
            return workbookToBytes(wb)
        }
    }

    private fun workbookToBytes(workbook: Workbook): ByteArray {
        val out = ByteArrayOutputStream()
        workbook.write(out)
        return out.toByteArray()
    }
}
