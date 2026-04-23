package io.github.salomax.neotool.banking.domain.import

import io.github.salomax.neotool.banking.model.dto.imports.ImportDuplicateCandidate
import io.github.salomax.neotool.banking.model.dto.imports.ImportProcessingResult
import io.github.salomax.neotool.banking.model.dto.imports.ImportReviewPayloadMapper
import io.github.salomax.neotool.banking.service.import.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.import.ImportJobRepository
import io.github.salomax.neotool.banking.repo.import.nfce.NfceDocumentRepository
import io.github.salomax.neotool.banking.repo.import.nfce.ProductAliasEntityRepository
import io.github.salomax.neotool.banking.repo.import.nfce.ProductVariantEntityRepository
import io.github.salomax.neotool.banking.repo.import.nfce.PurchaseItemEntityRepository
import io.github.salomax.neotool.banking.repo.import.nfce.PurchaseItemRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.service.import.dedup.SameSourceDedupService
import io.github.salomax.neotool.banking.service.import.dedup.CrossSourceDedupService
import io.github.salomax.neotool.banking.client.import.nfce.NfceClient
import io.github.salomax.neotool.banking.domain.import.nfce.NfceData
import io.github.salomax.neotool.banking.domain.import.nfce.NfceDocument
import io.github.salomax.neotool.banking.domain.import.nfce.NfceItem
import io.github.salomax.neotool.banking.domain.import.nfce.PurchaseItem
import io.github.salomax.neotool.banking.client.assets.ImportFileStoragePort
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.service.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.client.import.messaging.ImportFileProducer
import io.github.salomax.neotool.banking.client.import.messaging.ImportFileRequestedEvent
import io.github.salomax.neotool.common.error.BusinessRuleException
import io.github.salomax.neotool.common.error.ConflictException
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@DisplayName("ImportJobService")
class ImportJobServiceTest {
    private lateinit var importJobRepository: ImportJobRepository
    private lateinit var fileStorage: ImportFileStoragePort
    private lateinit var importFileProducer: ImportFileProducer
    private lateinit var nfceClient: NfceClient
    private lateinit var importProcessor: ImportProcessor
    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var crossSourceDedupService: CrossSourceDedupService
    private lateinit var sameSourceDedupService: SameSourceDedupService
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var snapshotComputationService: SnapshotComputationService
    private lateinit var nfceDocumentRepository: NfceDocumentRepository
    private lateinit var purchaseItemRepository: PurchaseItemRepository
    private lateinit var productVariantEntityRepository: ProductVariantEntityRepository
    private lateinit var productAliasEntityRepository: ProductAliasEntityRepository
    private lateinit var purchaseItemEntityRepository: PurchaseItemEntityRepository
    private lateinit var service: ImportJobService

    private lateinit var financialAccountId: UUID
    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private val now = Instant.now()
    private val today = LocalDate.of(2024, 1, 15)

    @BeforeEach
    fun setUp() {
        importJobRepository = mock()
        fileStorage = mock()
        importFileProducer = mock()
        nfceClient = mock()
        importProcessor = mock()
        financialAccountRepository = mock()
        crossSourceDedupService = mock()
        sameSourceDedupService = mock()
        transactionRepository = mock()
        snapshotComputationService = mock()
        nfceDocumentRepository = mock()
        purchaseItemRepository = mock()
        productVariantEntityRepository = mock()
        productAliasEntityRepository = mock()
        purchaseItemEntityRepository = mock()
        service =
            ImportJobService(
                importJobRepository = importJobRepository,
                fileStorage = fileStorage,
                importFileProducer = importFileProducer,
                nfceClient = nfceClient,
                importProcessor = importProcessor,
                financialAccountRepository = financialAccountRepository,
                crossSourceDedupService = crossSourceDedupService,
                sameSourceDedupService = sameSourceDedupService,
                transactionRepository = transactionRepository,
                snapshotComputationService = snapshotComputationService,
                nfceDocumentRepository = nfceDocumentRepository,
                purchaseItemRepository = purchaseItemRepository,
                productVariantEntityRepository = productVariantEntityRepository,
                productAliasEntityRepository = productAliasEntityRepository,
                purchaseItemEntityRepository = purchaseItemEntityRepository,
            )
        financialAccountId = UUID.randomUUID()
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
            listOf(
                FinancialAccount(
                    id = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    type = FinancialAccountType.BANK,
                    subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                    name = "Checking",
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        whenever(crossSourceDedupService.findManualNfceCandidates(any(), any(), any())).thenReturn(emptyList())
    }

    @Nested
    @DisplayName("initiateFileImport")
    inner class InitiateFileImportTests {
        @Test
        fun `Level 3 idempotency — same file twice returns same COMPLETED job`() {
            val fileBytes = "OFXHEADER:100\n<OFX>".toByteArray()
            val fileName = "export.ofx"
            val completedJob =
                ImportJob(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileName = fileName,
                    fileHash = "any-hash",
                    status = ImportJobStatus.COMPLETED,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(completedJob)

            val result =
                service.initiateFileImport(
                    financialAccountId,
                    accountId,
                    userId,
                    fileBytes,
                    fileName,
                    ImportSource.OFX,
                )

            assertThat(result.id).isEqualTo(completedJob.id)
            assertThat(result.status).isEqualTo(ImportJobStatus.COMPLETED)
            verify(importJobRepository, never()).save(any())
            verify(fileStorage, never()).store(any(), any(), any(), any())
            verify(importFileProducer, never()).publish(any())
        }

        @Test
        fun `throws conflict when same file hash already exists in PENDING`() {
            val fileBytes = "OFXHEADER:100\n<OFX>".toByteArray()
            val fileName = "export.ofx"
            val pendingJob =
                ImportJob(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileName = fileName,
                    fileHash = "any-hash",
                    status = ImportJobStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(pendingJob)

            val ex =
                assertThrows<ConflictException> {
                    service.initiateFileImport(
                        financialAccountId,
                        accountId,
                        userId,
                        fileBytes,
                        fileName,
                        ImportSource.OFX,
                    )
                }

            assertThat(ex.errorCode.code).isEqualTo(BankingErrorCode.IMPORT_FILE_ALREADY_EXISTS.code)
            verify(importJobRepository, never()).save(any())
            verify(fileStorage, never()).store(any(), any(), any(), any())
            verify(importFileProducer, never()).publish(any())
        }

        @Test
        fun `retries existing FAILED job with same hash by reusing job id`() {
            val fileBytes = "OFXHEADER:100\n<OFX>".toByteArray()
            val fileName = "export.ofx"
            val jobId = UUID.randomUUID()
            val assetId = UUID.randomUUID()
            val failedJob =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileName = fileName,
                    fileHash = "any-hash",
                    status = ImportJobStatus.FAILED,
                    errorCount = 1,
                    errorDetails = """{"message":"old error"}""",
                    assistantRequestPublishedAt = now,
                    completedAt = now,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(failedJob)
            whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(fileStorage.store(eq(accountId), any(), eq(fileName), any())).thenReturn(assetId)

            val result =
                service.initiateFileImport(
                    financialAccountId,
                    accountId,
                    userId,
                    fileBytes,
                    fileName,
                    ImportSource.OFX,
                )

            assertThat(result.id).isEqualTo(jobId)
            assertThat(result.status).isEqualTo(ImportJobStatus.PENDING)
            assertThat(result.errorCount).isNull()
            assertThat(result.errorDetails).isNull()
            assertThat(result.assistantRequestPublishedAt).isNull()
            verify(importJobRepository, never()).save(any())
            verify(importFileProducer).publish(
                eq(
                    ImportFileRequestedEvent(
                        jobId = jobId,
                        assetId = assetId,
                        source = ImportSource.OFX,
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                    ),
                ),
            )
        }

        @Test
        fun `retries existing PROCESSING job with same hash by reusing job id`() {
            val fileBytes = "OFXHEADER:100\n<OFX>".toByteArray()
            val fileName = "export.ofx"
            val jobId = UUID.randomUUID()
            val assetId = UUID.randomUUID()
            val processingJob =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.AUTO,
                    fileName = fileName,
                    fileHash = "any-hash",
                    status = ImportJobStatus.PROCESSING,
                    assistantRequestPublishedAt = now,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(processingJob)
            whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(fileStorage.store(eq(accountId), any(), eq(fileName), any())).thenReturn(assetId)

            val result =
                service.initiateFileImport(
                    financialAccountId,
                    accountId,
                    userId,
                    fileBytes,
                    fileName,
                    ImportSource.AUTO,
                )

            assertThat(result.id).isEqualTo(jobId)
            assertThat(result.status).isEqualTo(ImportJobStatus.PENDING)
            assertThat(result.assistantRequestPublishedAt).isNull()
            verify(importJobRepository, never()).save(any())
            verify(importFileProducer).publish(
                eq(
                    ImportFileRequestedEvent(
                        jobId = jobId,
                        assetId = assetId,
                        source = ImportSource.AUTO,
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                    ),
                ),
            )
        }

        @Test
        fun `retries existing COMPLETED job when previous outcome is empty`() {
            val fileBytes = "OFXHEADER:100\n<OFX>".toByteArray()
            val fileName = "export.ofx"
            val jobId = UUID.randomUUID()
            val assetId = UUID.randomUUID()
            val completedButEmpty =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileName = fileName,
                    fileHash = "any-hash",
                    status = ImportJobStatus.COMPLETED,
                    totalRows = 0,
                    importedCount = 0,
                    skippedCount = 0,
                    duplicateReviewCount = 0,
                    errorCount = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(completedButEmpty)
            whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(fileStorage.store(eq(accountId), any(), eq(fileName), any())).thenReturn(assetId)

            val result =
                service.initiateFileImport(
                    financialAccountId,
                    accountId,
                    userId,
                    fileBytes,
                    fileName,
                    ImportSource.OFX,
                )

            assertThat(result.id).isEqualTo(jobId)
            assertThat(result.status).isEqualTo(ImportJobStatus.PENDING)
            verify(importJobRepository).update(any())
            verify(importFileProducer).publish(
                eq(
                    ImportFileRequestedEvent(
                        jobId = jobId,
                        assetId = assetId,
                        source = ImportSource.OFX,
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                    ),
                ),
            )
        }

        @Test
        fun `stores file creates PENDING job and publishes event when no existing COMPLETED job`() {
            val fileBytes = "OFXHEADER:100\n<OFX>".toByteArray()
            val fileName = "export.ofx"
            val jobId = UUID.randomUUID()
            val assetId = UUID.randomUUID()
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(null)
            val savedJob =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileName = fileName,
                    fileHash = "any-hash",
                    status = ImportJobStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.save(any())).thenReturn(savedJob)
            whenever(fileStorage.store(eq(accountId), any(), eq(fileName), any())).thenReturn(assetId)

            val result =
                service.initiateFileImport(
                    financialAccountId,
                    accountId,
                    userId,
                    fileBytes,
                    fileName,
                    ImportSource.OFX,
                )

            assertThat(result.status).isEqualTo(ImportJobStatus.PENDING)
            assertThat(result.id).isEqualTo(jobId)
            verify(fileStorage).store(eq(accountId), eq(fileBytes), eq(fileName), any())
            verify(importFileProducer).publish(
                eq(
                    ImportFileRequestedEvent(
                        jobId = jobId,
                        assetId = assetId,
                        source = ImportSource.OFX,
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                    ),
                ),
            )
        }

        @Test
        fun `stores PDF import with application pdf mime type`() {
            val fileBytes = "%PDF-1.7".toByteArray()
            val fileName = "extrato.pdf"
            val jobId = UUID.randomUUID()
            val assetId = UUID.randomUUID()
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(null)
            val savedJob =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.AUTO,
                    fileName = fileName,
                    fileHash = "any-hash",
                    status = ImportJobStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.save(any())).thenReturn(savedJob)
            whenever(fileStorage.store(eq(accountId), any(), eq(fileName), any())).thenReturn(assetId)

            val result =
                service.initiateFileImport(
                    financialAccountId,
                    accountId,
                    userId,
                    fileBytes,
                    fileName,
                    null,
                )

            assertThat(result.id).isEqualTo(jobId)
            verify(fileStorage).store(eq(accountId), eq(fileBytes), eq(fileName), eq("application/pdf"))
            verify(importFileProducer).publish(any())
        }

        @Test
        fun `duplicate conflict when existing job belongs to another account`() {
            val fileBytes = "OFXHEADER:100\n<OFX>".toByteArray()
            val fileName = "export.ofx"
            val otherFinancialAccountId = UUID.randomUUID()
            val existingJob =
                ImportJob(
                    id = UUID.randomUUID(),
                    financialAccountId = otherFinancialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileName = fileName,
                    fileHash = "any-hash",
                    status = ImportJobStatus.PROCESSING,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(null)
            whenever(importJobRepository.findLatestByAccountAndFileHash(eq(accountId), any())).thenReturn(existingJob)
            whenever(importJobRepository.save(any())).thenThrow(
                RuntimeException(
                    "duplicate",
                    PSQLException(
                        "duplicate key value violates unique constraint \"uq_document_import_jobs_transaction_account_hash\"",
                        PSQLState.UNIQUE_VIOLATION,
                    ),
                ),
            )

            val ex =
                assertThrows<ConflictException> {
                    service.initiateFileImport(
                        financialAccountId,
                        accountId,
                        userId,
                        fileBytes,
                        fileName,
                        ImportSource.OFX,
                    )
                }

            assertThat(ex.errorCode.code).isEqualTo(BankingErrorCode.IMPORT_FILE_ALREADY_EXISTS.code)
            assertThat(ex.parameters["existingJobId"]).isEqualTo(existingJob.id.toString())
            assertThat(ex.parameters["existingFinancialAccountId"]).isEqualTo(otherFinancialAccountId.toString())
            verify(fileStorage, never()).store(any(), any(), any(), any())
            verify(importFileProducer, never()).publish(any())
        }

        @Test
        fun `marks job FAILED when queueing fails after save`() {
            val fileBytes = "OFXHEADER:100\n<OFX>".toByteArray()
            val fileName = "export.ofx"
            val jobId = UUID.randomUUID()
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(null)
            val savedJob =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileName = fileName,
                    fileHash = "any-hash",
                    status = ImportJobStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.save(any())).thenReturn(savedJob)
            whenever(fileStorage.store(eq(accountId), any(), eq(fileName), any())).thenThrow(
                RuntimeException("assets unavailable"),
            )

            val ex =
                assertThrows<BusinessRuleException> {
                    service.initiateFileImport(
                        financialAccountId,
                        accountId,
                        userId,
                        fileBytes,
                        fileName,
                        ImportSource.OFX,
                    )
                }

            assertThat(ex.errorCode.code).isEqualTo(BankingErrorCode.IMPORT_JOB_START_FAILED.code)
            verify(importJobRepository).update(any())
            verify(importFileProducer, never()).publish(any())
        }

        @Test
        fun `throws when financial account does not belong to account`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(emptyList())
            val fileBytes = "OFXHEADER:100".toByteArray()

            assertThrows<IllegalArgumentException> {
                service.initiateFileImport(financialAccountId, accountId, userId, fileBytes, "x.ofx", null)
            }
        }

        @Test
        fun `throws when async pipeline is unavailable and does not create job`() {
            val serviceWithoutProducer =
                ImportJobService(
                    importJobRepository = importJobRepository,
                    fileStorage = fileStorage,
                    importFileProducer = null,
                    nfceClient = nfceClient,
                    importProcessor = importProcessor,
                    financialAccountRepository = financialAccountRepository,
                    crossSourceDedupService = crossSourceDedupService,
                    sameSourceDedupService = sameSourceDedupService,
                    transactionRepository = transactionRepository,
                    snapshotComputationService = snapshotComputationService,
                    nfceDocumentRepository = nfceDocumentRepository,
                    purchaseItemRepository = purchaseItemRepository,
                    productVariantEntityRepository = productVariantEntityRepository,
                    productAliasEntityRepository = productAliasEntityRepository,
                    purchaseItemEntityRepository = purchaseItemEntityRepository,
                )
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(null)

            val ex =
                assertThrows<BusinessRuleException> {
                    serviceWithoutProducer.initiateFileImport(
                        financialAccountId,
                        accountId,
                        userId,
                        "OFXHEADER:100".toByteArray(),
                        "x.ofx",
                        ImportSource.OFX,
                    )
                }

            assertThat(ex.errorCode.code).isEqualTo(BankingErrorCode.IMPORT_ASYNC_PIPELINE_UNAVAILABLE.code)
            verify(importJobRepository, never()).save(any())
            verify(fileStorage, never()).store(any(), any(), any(), any())
        }
    }

    @Nested
    @DisplayName("importNfce")
    inner class ImportNfceTests {
        @Test
        fun `synchronous import creates transaction nfce document and purchase items`() {
            val qrCodeUrl = "https://sefaz.example/nfce?p=123"
            val chNFe = "35240112345678000199550010001234561123456789"
            val nfce =
                NfceData(
                    chNFe = chNFe,
                    dhEmi = now,
                    vNF = BigDecimal("99.99"),
                    cnpjEmitente = "12345678000190",
                    nomeEmitente = "Store Name",
                    municipio = "São Paulo",
                    uf = "SP",
                    items =
                        listOf(
                            NfceItem(
                                xProd = "ARROZ T1 5KG",
                                vProd = BigDecimal("25.00"),
                                qCom = BigDecimal("2"),
                                uCom = "UN",
                                ncm = "10063021",
                                cEAN = "789123",
                                cProd = "A1",
                            ),
                            NfceItem(
                                xProd = "FEIJAO PRETO 1KG",
                                vProd = BigDecimal("11.50"),
                                qCom = BigDecimal("1"),
                                uCom = "UN",
                                ncm = "07133319",
                                vUnCom = BigDecimal("11.50"),
                                vDesc = BigDecimal("1.50"),
                            ),
                        ),
                    cMunFG = "3550308",
                    cpfDestinatario = "32167209851",
                    vTotTrib = BigDecimal("12.34"),
                )
            whenever(nfceClient.fetch(qrCodeUrl)).thenReturn(nfce)
            whenever(
                sameSourceDedupService.isDuplicate(eq(financialAccountId), eq(chNFe), eq(TransactionSource.MANUAL)),
            ).thenReturn(false)
            whenever(importJobRepository.findByFileHash(any(), any())).thenReturn(null)
            val jobId = UUID.randomUUID()
            val pendingJob =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.NFC_E,
                    fileHash = chNFe,
                    status = ImportJobStatus.PENDING,
                    totalRows = 1,
                    importedCount = 1,
                    skippedCount = 0,
                    duplicateReviewCount = 0,
                    errorCount = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val completedJob =
                pendingJob.copy(
                    status = ImportJobStatus.COMPLETED,
                    totalRows = 1,
                    importedCount = 1,
                    skippedCount = 0,
                    duplicateReviewCount = 0,
                    errorCount = 0,
                    completedAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.save(any())).thenReturn(pendingJob)
            whenever(importJobRepository.update(any())).thenReturn(completedJob)
            val savedTransactionId = UUID.randomUUID()
            whenever(transactionRepository.save(any())).thenAnswer {
                it.getArgument<Transaction>(0).copy(id = savedTransactionId)
            }
            whenever(nfceDocumentRepository.save(any())).thenAnswer { it.getArgument<NfceDocument>(0).copy(id = UUID.randomUUID()) }
            whenever(purchaseItemRepository.saveAll(any())).thenAnswer { it.getArgument<List<PurchaseItem>>(0) }
            whenever(nfceDocumentRepository.findByTransactionId(any(), any())).thenAnswer { inv ->
                val txId = inv.arguments[0] as UUID
                val accId = inv.arguments[1] as UUID
                NfceDocument(
                    transactionId = txId,
                    accountId = accId,
                    accessKey = chNFe,
                    issuerCnpj = "12345678000190",
                    issuerName = "Store Name",
                    emissionDate = now,
                    totalValue = BigDecimal("99.99"),
                    state = "SP",
                    createdAt = now,
                )
            }

            val result = service.importNfce(financialAccountId, accountId, userId, qrCodeUrl)
            val documentCaptor = argumentCaptor<NfceDocument>()
            val itemsCaptor = argumentCaptor<List<PurchaseItem>>()

            assertThat(result.status).isEqualTo(ImportJobStatus.COMPLETED)
            assertThat(result.importedCount).isEqualTo(1)
            verify(transactionRepository).save(any())
            verify(nfceDocumentRepository).save(documentCaptor.capture())
            verify(purchaseItemRepository).saveAll(itemsCaptor.capture())
            verify(nfceClient).fetch(qrCodeUrl)
            assertThat(documentCaptor.firstValue.transactionId).isEqualTo(savedTransactionId)
            assertThat(documentCaptor.firstValue.issuerCnpj).isEqualTo("12345678000190")
            assertThat(documentCaptor.firstValue.totalTax).isEqualByComparingTo(BigDecimal("12.34"))
            assertThat(documentCaptor.firstValue.buyerCpf).isEqualTo("32167209851")
            assertThat(documentCaptor.firstValue.municipalityCode).isEqualTo("3550308")
            assertThat(itemsCaptor.firstValue).hasSize(2)
            assertThat(itemsCaptor.firstValue[0].unitPrice).isEqualByComparingTo(BigDecimal("12.5000"))
            assertThat(itemsCaptor.firstValue[0].effectivePrice).isEqualByComparingTo(BigDecimal("25.00"))
            assertThat(itemsCaptor.firstValue[1].unitPrice).isEqualByComparingTo(BigDecimal("11.50"))
            assertThat(itemsCaptor.firstValue[1].effectivePrice).isEqualByComparingTo(BigDecimal("10.00"))
        }

        @Test
        fun `synchronous import resolves product variant ids through layers 1 to 3`() {
            val qrCodeUrl = "https://sefaz.example/nfce?p=123"
            val chNFe = "35240112345678000199550010001234561123456789"
            val eanVariantId = UUID.randomUUID()
            val aliasVariantId = UUID.randomUUID()
            val cnpjVariantId = UUID.randomUUID()
            val nfce =
                NfceData(
                    chNFe = chNFe,
                    dhEmi = now,
                    vNF = BigDecimal("60.00"),
                    cnpjEmitente = "12.345.678/0001-90",
                    nomeEmitente = "Store Name",
                    municipio = "São Paulo",
                    uf = "SP",
                    items =
                        listOf(
                            NfceItem("ITEM EAN", BigDecimal("10.00"), BigDecimal.ONE, "UN", "10063021", cEAN = "789111"),
                            NfceItem("ITEM ALIAS", BigDecimal("20.00"), BigDecimal.ONE, "UN", "10063021"),
                            NfceItem("ITEM CPROD", BigDecimal("30.00"), BigDecimal.ONE, "UN", "10063021", cProd = "CP-1"),
                            NfceItem("ITEM NOVO", BigDecimal("0.00"), BigDecimal.ONE, "UN", ""),
                        ),
                )
            whenever(nfceClient.fetch(qrCodeUrl)).thenReturn(nfce)
            whenever(
                sameSourceDedupService.isDuplicate(eq(financialAccountId), eq(chNFe), eq(TransactionSource.MANUAL)),
            ).thenReturn(false)
            whenever(importJobRepository.findByFileHash(any(), any())).thenReturn(null)
            val pendingJob =
                ImportJob(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.NFC_E,
                    fileHash = chNFe,
                    status = ImportJobStatus.PENDING,
                    totalRows = 1,
                    importedCount = 1,
                    skippedCount = 0,
                    duplicateReviewCount = 0,
                    errorCount = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.save(any())).thenReturn(pendingJob)
            whenever(importJobRepository.update(any())).thenReturn(
                pendingJob.copy(
                    status = ImportJobStatus.COMPLETED,
                    totalRows = 1,
                    importedCount = 1,
                    skippedCount = 0,
                    duplicateReviewCount = 0,
                    errorCount = 0,
                    completedAt = now,
                    updatedAt = now,
                ),
            )
            whenever(transactionRepository.save(any())).thenAnswer {
                it.getArgument<Transaction>(0).copy(id = UUID.randomUUID())
            }
            whenever(nfceDocumentRepository.save(any())).thenAnswer { it.getArgument(0) }
            whenever(purchaseItemRepository.saveAll(any())).thenAnswer { it.getArgument<List<PurchaseItem>>(0) }
            whenever(nfceDocumentRepository.findByTransactionId(any(), any())).thenAnswer { inv ->
                val txId = inv.arguments[0] as UUID
                val accId = inv.arguments[1] as UUID
                NfceDocument(
                    transactionId = txId,
                    accountId = accId,
                    accessKey = chNFe,
                    issuerCnpj = "12345678000190",
                    issuerName = "Store Name",
                    emissionDate = now,
                    totalValue = BigDecimal("60.00"),
                    state = "SP",
                    createdAt = now,
                )
            }
            whenever(productVariantEntityRepository.findByEan("789111")).thenReturn(
                io.github.salomax.neotool.banking.model.import.nfce.ProductVariantEntity(
                    id = eanVariantId,
                    productId = UUID.randomUUID(),
                    accountId = UUID.randomUUID(),
                    name = "By EAN",
                ),
            )
            whenever(productAliasEntityRepository.findByAccountIdAndRawDescription(accountId, "ITEM ALIAS")).thenReturn(
                io.github.salomax.neotool.banking.model.import.nfce.ProductAliasEntity(
                    id = UUID.randomUUID(),
                    productVariantId = aliasVariantId,
                    accountId = accountId,
                    rawDescription = "ITEM ALIAS",
                    source = "MANUAL",
                ),
            )
            whenever(
                purchaseItemEntityRepository.findFirstResolvedByCnpjAndProduct(
                    accountId = accountId,
                    issuerCnpj = "12345678000190",
                    issuerProductCode = "CP-1",
                ),
            ).thenReturn(
                io.github.salomax.neotool.banking.model.import.nfce.PurchaseItemEntity(
                    id = UUID.randomUUID(),
                    transactionId = UUID.randomUUID(),
                    accountId = accountId,
                    productVariantId = cnpjVariantId,
                    sequence = 1,
                    rawDescription = "ITEM CPROD",
                    quantity = BigDecimal.ONE,
                    unit = "UN",
                    unitPrice = BigDecimal.ONE,
                    totalPrice = BigDecimal.ONE,
                    effectivePrice = BigDecimal.ONE,
                ),
            )

            service.importNfce(financialAccountId, accountId, userId, qrCodeUrl)
            val itemsCaptor = argumentCaptor<List<PurchaseItem>>()

            verify(purchaseItemRepository).saveAll(itemsCaptor.capture())
            assertThat(itemsCaptor.firstValue.map { it.productVariantId })
                .containsExactly(eanVariantId, aliasVariantId, cnpjVariantId, null)
        }

        @Test
        fun `does not mark job completed when NFC-e document persistence fails`() {
            val qrCodeUrl = "https://sefaz.example/nfce?p=failure"
            val chNFe = "35240112345678000199550010001234561123999999"
            val nfce =
                NfceData(
                    chNFe = chNFe,
                    dhEmi = now,
                    vNF = BigDecimal("42.00"),
                    cnpjEmitente = "12.345.678/0001-90",
                    nomeEmitente = "Store Name",
                    municipio = "São Paulo",
                    uf = "SP",
                    items = emptyList(),
                )
            whenever(nfceClient.fetch(qrCodeUrl)).thenReturn(nfce)
            whenever(
                sameSourceDedupService.isDuplicate(eq(financialAccountId), eq(chNFe), eq(TransactionSource.MANUAL)),
            ).thenReturn(false)
            whenever(importJobRepository.findByFileHash(any(), any())).thenReturn(null)
            val pendingJob =
                ImportJob(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.NFC_E,
                    fileHash = chNFe,
                    status = ImportJobStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.save(any())).thenReturn(pendingJob)
            whenever(transactionRepository.save(any())).thenAnswer {
                it.getArgument<Transaction>(0).copy(id = UUID.randomUUID())
            }
            whenever(nfceDocumentRepository.save(any())).thenThrow(IllegalStateException("insert failed"))

            assertThrows<IllegalStateException> {
                service.importNfce(financialAccountId, accountId, userId, qrCodeUrl)
            }

            verify(importJobRepository, never()).update(any())
            verify(purchaseItemRepository, never()).saveAll(any())
        }

        @Test
        fun `manual match puts NFC-e import into review instead of creating a new transaction`() {
            val qrCodeUrl = "https://sefaz.example/nfce?p=review"
            val chNFe = "35240112345678000199550010001234561123450000"
            val existingTx =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("157.32"),
                    date = LocalDate.ofInstant(now, ZoneId.systemDefault()),
                    description = "Compra mercado",
                    source = TransactionSource.MANUAL,
                    status = TransactionStatus.POSTED,
                    createdAt = now,
                    updatedAt = now,
                )
            val nfce =
                NfceData(
                    chNFe = chNFe,
                    dhEmi = now,
                    vNF = BigDecimal("157.32"),
                    cnpjEmitente = "12.345.678/0001-90",
                    nomeEmitente = "Supermercado Exemplo",
                    municipio = "São Paulo",
                    uf = "SP",
                    items = emptyList(),
                )
            whenever(nfceClient.fetch(qrCodeUrl)).thenReturn(nfce)
            whenever(sameSourceDedupService.isDuplicate(eq(financialAccountId), eq(chNFe), eq(TransactionSource.MANUAL)))
                .thenReturn(false)
            whenever(importJobRepository.findByFileHash(financialAccountId, chNFe)).thenReturn(null)
            whenever(
                crossSourceDedupService.findManualNfceCandidates(
                    eq(financialAccountId),
                    eq(BigDecimal("157.32")),
                    any(),
                ),
            ).thenReturn(listOf(existingTx))
            whenever(importJobRepository.save(any())).thenAnswer { invocation ->
                invocation.getArgument<ImportJob>(0).copy(id = UUID.randomUUID())
            }

            val result = service.importNfce(financialAccountId, accountId, userId, qrCodeUrl)

            assertThat(result.status).isEqualTo(ImportJobStatus.REVIEW_REQUIRED)
            assertThat(result.duplicateReviewCount).isEqualTo(1)
            assertThat(result.reviewPayload).contains("nfce_manual_link")
            verify(transactionRepository, never()).save(any())
            verify(nfceDocumentRepository, never()).save(any())
            verify(purchaseItemRepository, never()).saveAll(any())
        }

        @Test
        fun `duplicate chNFe returns existing job when found`() {
            val chNFe = "35240112345678000199550010001234561123456789"
            whenever(nfceClient.fetch(any())).thenReturn(
                NfceData(
                    chNFe = chNFe,
                    dhEmi = now,
                    vNF = BigDecimal.ONE,
                    cnpjEmitente = "",
                    nomeEmitente = "",
                    municipio = "",
                    uf = "",
                    items = emptyList(),
                ),
            )
            whenever(sameSourceDedupService.isDuplicate(any(), eq(chNFe), any())).thenReturn(true)
            val existingJob =
                ImportJob(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.NFC_E,
                    fileHash = chNFe,
                    status = ImportJobStatus.COMPLETED,
                    importedCount = 1,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findByFileHash(financialAccountId, chNFe)).thenReturn(existingJob)
            whenever(transactionRepository.existsByImportJobId(requireNotNull(existingJob.id))).thenReturn(true)

            val result = service.importNfce(financialAccountId, accountId, userId, "https://example.com")

            assertThat(result.id).isEqualTo(existingJob.id)
            verify(transactionRepository, never()).save(any())
            verify(nfceDocumentRepository, never()).save(any())
            verify(purchaseItemRepository, never()).saveAll(any())
            verify(importJobRepository, never()).save(any())
        }

        @Test
        fun `duplicate chNFe with deleted original transaction allows fresh import`() {
            val qrCodeUrl = "https://sefaz.example/nfce?p=reimport"
            val chNFe = "35240112345678000199550010001234561123456789"
            val existingJobId = UUID.randomUUID()
            val existingJob =
                ImportJob(
                    id = existingJobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.NFC_E,
                    fileHash = chNFe,
                    status = ImportJobStatus.COMPLETED,
                    importedCount = 1,
                    createdAt = now,
                    updatedAt = now,
                )
            val nfce =
                NfceData(
                    chNFe = chNFe,
                    dhEmi = now,
                    vNF = BigDecimal("15.00"),
                    cnpjEmitente = "12345678000190",
                    nomeEmitente = "Store Name",
                    municipio = "São Paulo",
                    uf = "SP",
                    items = emptyList(),
                )
            val pendingJobId = existingJobId
            val pendingJob =
                ImportJob(
                    id = pendingJobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.NFC_E,
                    fileHash = chNFe,
                    status = ImportJobStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                )
            val completedJob =
                pendingJob.copy(
                    status = ImportJobStatus.COMPLETED,
                    totalRows = 1,
                    importedCount = 1,
                    skippedCount = 0,
                    duplicateReviewCount = 0,
                    errorCount = 0,
                    completedAt = now,
                    updatedAt = now,
                )
            whenever(nfceClient.fetch(qrCodeUrl)).thenReturn(nfce)
            whenever(importJobRepository.findByFileHash(financialAccountId, chNFe)).thenReturn(existingJob)
            whenever(transactionRepository.existsByImportJobId(existingJobId)).thenReturn(false)
            whenever(sameSourceDedupService.isDuplicate(eq(financialAccountId), eq(chNFe), eq(TransactionSource.MANUAL))).thenReturn(false)
            whenever(importJobRepository.update(any())).thenReturn(pendingJob, completedJob)
            whenever(transactionRepository.clearProviderTransactionIdForDeleted(financialAccountId, chNFe)).thenReturn(1)
            whenever(transactionRepository.save(any())).thenAnswer {
                it.getArgument<Transaction>(0).copy(id = UUID.randomUUID())
            }
            whenever(nfceDocumentRepository.deleteByAccessKeyAndAccountIdForDeletedTransactions(eq(chNFe), eq(accountId))).thenReturn(1)
            whenever(nfceDocumentRepository.save(any())).thenAnswer { it.getArgument<NfceDocument>(0).copy(id = UUID.randomUUID()) }
            whenever(nfceDocumentRepository.findByTransactionId(any(), eq(accountId))).thenAnswer {
                NfceDocument(
                    id = UUID.randomUUID(),
                    transactionId = it.getArgument(0),
                    accountId = accountId,
                    accessKey = chNFe,
                    issuerCnpj = "12345678000190",
                    issuerName = "Store Name",
                    emissionDate = now,
                    totalValue = BigDecimal("15.00"),
                    state = "SP",
                    countryCode = "BR",
                    createdAt = now,
                )
            }
            whenever(purchaseItemRepository.saveAll(any())).thenAnswer { it.getArgument<List<PurchaseItem>>(0) }

            val result = service.importNfce(financialAccountId, accountId, userId, qrCodeUrl)

            assertThat(result.id).isEqualTo(existingJobId)
            assertThat(result.status).isEqualTo(ImportJobStatus.COMPLETED)
            verify(importJobRepository, never()).save(any())
            verify(transactionRepository).clearProviderTransactionIdForDeleted(financialAccountId, chNFe)
            verify(transactionRepository).save(any())
            verify(nfceDocumentRepository).deleteByAccessKeyAndAccountIdForDeletedTransactions(chNFe, accountId)
        }

        @Test
        fun `duplicate chNFe creates skipped job when no existing job`() {
            val chNFe = "35240112345678000199550010001234561123456789"
            whenever(nfceClient.fetch(any())).thenReturn(
                NfceData(
                    chNFe = chNFe,
                    dhEmi = now,
                    vNF = BigDecimal.ONE,
                    cnpjEmitente = "",
                    nomeEmitente = "",
                    municipio = "",
                    uf = "",
                    items = emptyList(),
                ),
            )
            whenever(sameSourceDedupService.isDuplicate(any(), eq(chNFe), any())).thenReturn(true)
            whenever(importJobRepository.findByFileHash(financialAccountId, chNFe)).thenReturn(null)
            whenever(importJobRepository.save(any())).thenAnswer { it.getArgument(0) }

            val result = service.importNfce(financialAccountId, accountId, userId, "https://example.com")

            assertThat(result.status).isEqualTo(ImportJobStatus.COMPLETED)
            assertThat(result.importedCount).isEqualTo(0)
            assertThat(result.skippedCount).isEqualTo(1)
            verify(transactionRepository, never()).save(any())
            verify(nfceDocumentRepository, never()).save(any())
            verify(purchaseItemRepository, never()).saveAll(any())
        }
    }

    @Nested
    @DisplayName("getJob and listJobs")
    inner class GetAndListTests {
        @Test
        fun `getJob returns job when found`() {
            val jobId = UUID.randomUUID()
            val job =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileHash = "h",
                    status = ImportJobStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findById(jobId, accountId)).thenReturn(job)

            val result = service.getJob(jobId, accountId)

            assertThat(result).isEqualTo(job)
        }

        @Test
        fun `getJob returns null when not found`() {
            whenever(importJobRepository.findById(any(), any())).thenReturn(null)
            assertThat(service.getJob(UUID.randomUUID(), accountId)).isNull()
        }

        @Test
        fun `listJobs with financialAccountId uses findByFinancialAccount`() {
            val list =
                listOf(
                    ImportJob(
                        id = UUID.randomUUID(),
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        source = ImportSource.OFX,
                        fileHash = "h",
                        status = ImportJobStatus.COMPLETED,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            whenever(importJobRepository.findByFinancialAccount(financialAccountId, accountId)).thenReturn(list)

            val result = service.listJobs(financialAccountId, accountId)

            assertThat(result).isEqualTo(list)
        }

        @Test
        fun `listJobs without financialAccountId uses findByAccount`() {
            val list =
                listOf(
                    ImportJob(
                        id = UUID.randomUUID(),
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        source = ImportSource.OFX,
                        fileHash = "h",
                        status = ImportJobStatus.COMPLETED,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            whenever(importJobRepository.findByAccount(accountId)).thenReturn(list)

            val result = service.listJobs(null, accountId)

            assertThat(result).isEqualTo(list)
        }
    }

    @Nested
    @DisplayName("resolveImportDuplicate")
    inner class ResolveImportDuplicateTests {
        private val objectMapper = jacksonObjectMapper().apply { registerModule(JavaTimeModule()) }

        @Test
        fun `USE_INCOMING soft-deletes existing and commits incoming`() {
            val jobId = UUID.randomUUID()
            val existingTxId = UUID.randomUUID()
            val parsed =
                ParsedTransaction(
                    providerTransactionId = "pid-1",
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = BigDecimal("50.00"),
                    date = today,
                    description = "Incoming",
                    currencyCode = "BRL",
                )
            val existingTx =
                Transaction(
                    id = existingTxId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("50.00"),
                    date = today,
                    description = "Existing",
                    source = TransactionSource.MANUAL,
                    status = TransactionStatus.POSTED,
                    createdAt = now,
                    updatedAt = now,
                )
            val candidate = ImportDuplicateCandidate(rowIndex = 0, parsed = parsed, candidates = listOf(existingTx))
            val reviewPayload = objectMapper.writeValueAsString(listOf(candidate))
            val job =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.CSV_NUBANK,
                    fileHash = "h",
                    status = ImportJobStatus.REVIEW_REQUIRED,
                    totalRows = 1,
                    importedCount = 0,
                    duplicateReviewCount = 1,
                    errorCount = 0,
                    reviewPayload = reviewPayload,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findById(jobId, accountId)).thenReturn(job)
            whenever(transactionRepository.softDelete(existingTxId, accountId)).thenReturn(true)
            val savedIncoming = existingTx.copy(id = UUID.randomUUID(), description = "Incoming")
            whenever(importProcessor.process(eq(job), any(), eq(true))).thenReturn(
                ImportProcessingResult(
                    imported = listOf(savedIncoming),
                    skipped = 0,
                    duplicateCandidates = emptyList(),
                    errors = emptyList(),
                ),
            )
            whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

            val result = service.resolveImportDuplicate(jobId, 0, DuplicateResolution.USE_INCOMING, accountId)

            verify(transactionRepository).softDelete(existingTxId, accountId)
            verify(importProcessor).process(eq(job), any(), eq(true))
            assertThat(result.status).isEqualTo(ImportJobStatus.COMPLETED)
            assertThat(result.importedCount).isEqualTo(1)
            assertThat(result.duplicateReviewCount).isEqualTo(0)
        }

        @Test
        fun `resolves duplicate from canonical review payload envelope`() {
            val jobId = UUID.randomUUID()
            val candidate =
                ImportDuplicateCandidate(
                    rowIndex = 0,
                    parsed =
                        ParsedTransaction(
                            creditDebitType = CreditDebitType.DEBIT,
                            amount = BigDecimal("10"),
                            date = today,
                            description = "A",
                            currencyCode = "BRL",
                        ),
                    candidates = emptyList(),
                )
            val reviewPayload =
                ImportReviewPayloadMapper.toJson(
                    objectMapper = objectMapper,
                    duplicateCandidates = listOf(candidate),
                    gate = mapOf("gate_reason" to "llm_low_confidence"),
                )
            val job =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileHash = "h",
                    status = ImportJobStatus.REVIEW_REQUIRED,
                    totalRows = 1,
                    importedCount = 0,
                    duplicateReviewCount = 1,
                    errorCount = 0,
                    reviewPayload = reviewPayload,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findById(jobId, accountId)).thenReturn(job)
            whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

            val result = service.resolveImportDuplicate(jobId, 0, DuplicateResolution.KEEP_EXISTING, accountId)

            assertThat(result.status).isEqualTo(ImportJobStatus.COMPLETED)
            assertThat(result.duplicateReviewCount).isEqualTo(0)
            assertThat(result.reviewPayload).isNull()
        }

        @Test
        fun `NFC-e USE_INCOMING links and enriches existing manual transaction`() {
            val jobId = UUID.randomUUID()
            val existingTxId = UUID.randomUUID()
            val existingTx =
                Transaction(
                    id = existingTxId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("157.32"),
                    date = today.minusDays(1),
                    description = "Mercado manual",
                    source = TransactionSource.MANUAL,
                    status = TransactionStatus.POSTED,
                    createdAt = now,
                    updatedAt = now,
                )
            val candidate =
                ImportDuplicateCandidate(
                    rowIndex = 0,
                    parsed =
                        ParsedTransaction(
                            providerTransactionId = "35240112345678000199550010001234561123450000",
                            creditDebitType = CreditDebitType.DEBIT,
                            amount = BigDecimal("157.32"),
                            date = LocalDate.ofInstant(now, ZoneId.systemDefault()),
                            description = "Supermercado Exemplo",
                            currencyCode = "BRL",
                        ),
                    candidates = listOf(existingTx),
                )
            val job =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.NFC_E,
                    fileHash = "35240112345678000199550010001234561123450000",
                    status = ImportJobStatus.REVIEW_REQUIRED,
                    extractionMetadata = """{"qrCodeUrl":"https://sefaz.example/nfce?p=link"}""",
                    totalRows = 1,
                    importedCount = 0,
                    duplicateReviewCount = 1,
                    errorCount = 0,
                    reviewPayload =
                        ImportReviewPayloadMapper.toJson(
                            objectMapper = objectMapper,
                            duplicateCandidates = listOf(candidate),
                            gate = mapOf("kind" to "nfce_manual_link"),
                        ),
                    createdAt = now,
                    updatedAt = now,
                )
            val nfce =
                NfceData(
                    chNFe = job.fileHash,
                    dhEmi = now,
                    vNF = BigDecimal("157.32"),
                    cnpjEmitente = "12.345.678/0001-90",
                    nomeEmitente = "Supermercado Exemplo",
                    municipio = "São Paulo",
                    uf = "SP",
                    items =
                        listOf(
                            NfceItem(
                                xProd = "ARROZ 5KG",
                                vProd = BigDecimal("30.00"),
                                qCom = BigDecimal.ONE,
                                uCom = "UN",
                                ncm = "10063021",
                            ),
                        ),
                )

            whenever(importJobRepository.findById(jobId, accountId)).thenReturn(job)
            whenever(nfceClient.fetch("https://sefaz.example/nfce?p=link")).thenReturn(nfce)
            whenever(nfceDocumentRepository.findByTransactionId(existingTxId, accountId))
                .thenReturn(null)
                .thenReturn(
                    NfceDocument(
                        transactionId = existingTxId,
                        accountId = accountId,
                        accessKey = job.fileHash,
                        issuerCnpj = "12345678000190",
                        issuerName = "Supermercado Exemplo",
                        emissionDate = now,
                        totalValue = BigDecimal("157.32"),
                        state = "SP",
                        createdAt = now,
                    ),
                )
            whenever(transactionRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(nfceDocumentRepository.save(any())).thenAnswer { it.getArgument(0) }
            whenever(purchaseItemRepository.saveAll(any())).thenAnswer { it.getArgument<List<PurchaseItem>>(0) }
            whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

            val result = service.resolveImportDuplicate(jobId, 0, DuplicateResolution.USE_INCOMING, accountId)
            val updatedCaptor = argumentCaptor<Transaction>()
            val documentCaptor = argumentCaptor<NfceDocument>()

            verify(transactionRepository).update(updatedCaptor.capture())
            verify(nfceDocumentRepository).save(documentCaptor.capture())
            verify(purchaseItemRepository).saveAll(any())
            assertThat(updatedCaptor.firstValue.id).isEqualTo(existingTxId)
            assertThat(updatedCaptor.firstValue.providerTransactionId).isEqualTo(job.fileHash)
            assertThat(updatedCaptor.firstValue.counterpartyName).isEqualTo("Supermercado Exemplo")
            assertThat(documentCaptor.firstValue.transactionId).isEqualTo(existingTxId)
            assertThat(result.status).isEqualTo(ImportJobStatus.COMPLETED)
            assertThat(result.importedCount).isEqualTo(1)
        }

        @Test
        fun `updates to COMPLETED only when all candidates resolved`() {
            val jobId = UUID.randomUUID()
            val parsed0 =
                ParsedTransaction(
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = BigDecimal("10"),
                    date = today,
                    description = "A",
                    currencyCode = "BRL",
                )
            val parsed1 =
                ParsedTransaction(
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = BigDecimal("20"),
                    date = today,
                    description = "B",
                    currencyCode = "BRL",
                )
            val existing =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10"),
                    date = today,
                    description = "A",
                    source = TransactionSource.MANUAL,
                    status = TransactionStatus.POSTED,
                    createdAt = now,
                    updatedAt = now,
                )
            val candidates =
                listOf(
                    ImportDuplicateCandidate(rowIndex = 0, parsed = parsed0, candidates = listOf(existing)),
                    ImportDuplicateCandidate(rowIndex = 1, parsed = parsed1, candidates = emptyList()),
                )
            val reviewPayload = objectMapper.writeValueAsString(candidates)
            val job =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileHash = "h",
                    status = ImportJobStatus.REVIEW_REQUIRED,
                    totalRows = 2,
                    importedCount = 0,
                    duplicateReviewCount = 2,
                    errorCount = 0,
                    reviewPayload = reviewPayload,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findById(jobId, accountId)).thenReturn(job)
            whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

            val afterFirst = service.resolveImportDuplicate(jobId, 0, DuplicateResolution.KEEP_EXISTING, accountId)

            assertThat(afterFirst.status).isEqualTo(ImportJobStatus.REVIEW_REQUIRED)
            assertThat(afterFirst.duplicateReviewCount).isEqualTo(1)

            whenever(importJobRepository.findById(jobId, accountId)).thenReturn(afterFirst)

            val afterSecond = service.resolveImportDuplicate(jobId, 1, DuplicateResolution.KEEP_EXISTING, accountId)

            assertThat(afterSecond.status).isEqualTo(ImportJobStatus.COMPLETED)
            assertThat(afterSecond.duplicateReviewCount).isEqualTo(0)
        }

        @Test
        fun `throws when job is not REVIEW_REQUIRED`() {
            val jobId = UUID.randomUUID()
            val job =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileHash = "h",
                    status = ImportJobStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findById(jobId, accountId)).thenReturn(job)

            assertThrows<IllegalArgumentException> {
                service.resolveImportDuplicate(jobId, 0, DuplicateResolution.KEEP_BOTH, accountId)
            }
        }

        @Test
        fun `throws when candidate rowIndex not found`() {
            val jobId = UUID.randomUUID()
            val candidate =
                ImportDuplicateCandidate(
                    rowIndex = 5,
                    parsed =
                        ParsedTransaction(
                            creditDebitType = CreditDebitType.DEBIT,
                            amount = BigDecimal.ONE,
                            date = today,
                            description = "x",
                            currencyCode = "BRL",
                        ),
                    candidates = emptyList(),
                )
            val job =
                ImportJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.OFX,
                    fileHash = "h",
                    status = ImportJobStatus.REVIEW_REQUIRED,
                    duplicateReviewCount = 1,
                    reviewPayload = objectMapper.writeValueAsString(listOf(candidate)),
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(importJobRepository.findById(jobId, accountId)).thenReturn(job)

            assertThrows<IllegalArgumentException> {
                service.resolveImportDuplicate(jobId, 0, DuplicateResolution.KEEP_EXISTING, accountId)
            }
        }
    }
}
