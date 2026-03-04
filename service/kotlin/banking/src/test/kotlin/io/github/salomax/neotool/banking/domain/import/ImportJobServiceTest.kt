package io.github.salomax.neotool.banking.domain.import

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.import.dedup.SameSourceDedupService
import io.github.salomax.neotool.banking.domain.import.nfce.NfceClient
import io.github.salomax.neotool.banking.domain.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.domain.import.nfce.NfceData
import io.github.salomax.neotool.banking.domain.import.port.ImportFileStoragePort
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.infra.kafka.ImportFileProducer
import io.github.salomax.neotool.banking.infra.kafka.ImportFileRequestedEvent
import io.github.salomax.neotool.common.error.BusinessRuleException
import io.github.salomax.neotool.common.error.ConflictException
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

@DisplayName("ImportJobService")
class ImportJobServiceTest {

    private lateinit var importJobRepository: ImportJobRepository
    private lateinit var fileStorage: ImportFileStoragePort
    private lateinit var importFileProducer: ImportFileProducer
    private lateinit var nfceClient: NfceClient
    private lateinit var importProcessor: ImportProcessor
    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var sameSourceDedupService: SameSourceDedupService
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var snapshotComputationService: SnapshotComputationService
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
        sameSourceDedupService = mock()
        transactionRepository = mock()
        snapshotComputationService = mock()
        service = ImportJobService(
            importJobRepository = importJobRepository,
            fileStorage = fileStorage,
            importFileProducer = importFileProducer,
            nfceClient = nfceClient,
            importProcessor = importProcessor,
            financialAccountRepository = financialAccountRepository,
            sameSourceDedupService = sameSourceDedupService,
            transactionRepository = transactionRepository,
            snapshotComputationService = snapshotComputationService,
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
    }

    @Nested
    @DisplayName("initiateFileImport")
    inner class InitiateFileImportTests {

        @Test
        fun `Level 3 idempotency — same file twice returns same COMPLETED job`() {
            val fileBytes = "OFXHEADER:100\n<OFX>".toByteArray()
            val fileName = "export.ofx"
            val completedJob = ImportJob(
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

            val result = service.initiateFileImport(financialAccountId, accountId, userId, fileBytes, fileName, ImportSource.OFX)

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
            val pendingJob = ImportJob(
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

            val ex = assertThrows<ConflictException> {
                service.initiateFileImport(financialAccountId, accountId, userId, fileBytes, fileName, ImportSource.OFX)
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
            val failedJob = ImportJob(
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
                completedAt = now,
                createdAt = now,
                updatedAt = now,
            )
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(failedJob)
            whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(fileStorage.store(eq(accountId), any(), eq(fileName), any())).thenReturn(assetId)

            val result = service.initiateFileImport(financialAccountId, accountId, userId, fileBytes, fileName, ImportSource.OFX)

            assertThat(result.id).isEqualTo(jobId)
            assertThat(result.status).isEqualTo(ImportJobStatus.PENDING)
            assertThat(result.errorCount).isNull()
            assertThat(result.errorDetails).isNull()
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
        fun `retries existing COMPLETED job when previous outcome is empty`() {
            val fileBytes = "OFXHEADER:100\n<OFX>".toByteArray()
            val fileName = "export.ofx"
            val jobId = UUID.randomUUID()
            val assetId = UUID.randomUUID()
            val completedButEmpty = ImportJob(
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

            val result = service.initiateFileImport(financialAccountId, accountId, userId, fileBytes, fileName, ImportSource.OFX)

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
            val savedJob = ImportJob(
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

            val result = service.initiateFileImport(financialAccountId, accountId, userId, fileBytes, fileName, ImportSource.OFX)

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
        fun `marks job FAILED when queueing fails after save`() {
            val fileBytes = "OFXHEADER:100\n<OFX>".toByteArray()
            val fileName = "export.ofx"
            val jobId = UUID.randomUUID()
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(null)
            val savedJob = ImportJob(
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

            val ex = assertThrows<BusinessRuleException> {
                service.initiateFileImport(financialAccountId, accountId, userId, fileBytes, fileName, ImportSource.OFX)
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
            val serviceWithoutProducer = ImportJobService(
                importJobRepository = importJobRepository,
                fileStorage = fileStorage,
                importFileProducer = null,
                nfceClient = nfceClient,
                importProcessor = importProcessor,
                financialAccountRepository = financialAccountRepository,
                sameSourceDedupService = sameSourceDedupService,
                transactionRepository = transactionRepository,
                snapshotComputationService = snapshotComputationService,
            )
            whenever(importJobRepository.findByFileHash(eq(financialAccountId), any())).thenReturn(null)

            val ex = assertThrows<BusinessRuleException> {
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
        fun `synchronous import creates transaction and COMPLETED job`() {
            val qrCodeUrl = "https://sefaz.example/nfce?p=123"
            val chNFe = "35240112345678000199550010001234561123456789"
            val nfce = NfceData(
                chNFe = chNFe,
                dhEmi = now,
                vNF = BigDecimal("99.99"),
                cnpjEmitente = "12.345.678/0001-90",
                nomeEmitente = "Store Name",
                municipio = "São Paulo",
                uf = "SP",
                items = emptyList(),
            )
            whenever(nfceClient.fetch(qrCodeUrl)).thenReturn(nfce)
            whenever(sameSourceDedupService.isDuplicate(eq(financialAccountId), eq(chNFe), eq(TransactionSource.MANUAL))).thenReturn(false)
            whenever(importJobRepository.findByFileHash(any(), any())).thenReturn(null)
            val jobId = UUID.randomUUID()
            val savedJob = ImportJob(
                id = jobId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                source = ImportSource.NFC_E,
                fileHash = chNFe,
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
            whenever(importJobRepository.save(any())).thenReturn(savedJob)
            whenever(transactionRepository.save(any())).thenAnswer { it.getArgument(0) }

            val result = service.importNfce(financialAccountId, accountId, userId, qrCodeUrl)

            assertThat(result.status).isEqualTo(ImportJobStatus.COMPLETED)
            assertThat(result.importedCount).isEqualTo(1)
            verify(transactionRepository).save(any())
            verify(nfceClient).fetch(qrCodeUrl)
        }

        @Test
        fun `duplicate chNFe returns existing job when found`() {
            val chNFe = "35240112345678000199550010001234561123456789"
            whenever(nfceClient.fetch(any())).thenReturn(
                NfceData(chNFe = chNFe, dhEmi = now, vNF = BigDecimal.ONE, cnpjEmitente = "", nomeEmitente = "", municipio = "", uf = "", items = emptyList()),
            )
            whenever(sameSourceDedupService.isDuplicate(any(), eq(chNFe), any())).thenReturn(true)
            val existingJob = ImportJob(
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

            val result = service.importNfce(financialAccountId, accountId, userId, "https://example.com")

            assertThat(result.id).isEqualTo(existingJob.id)
            verify(transactionRepository, never()).save(any())
            verify(importJobRepository, never()).save(any())
        }

        @Test
        fun `duplicate chNFe creates skipped job when no existing job`() {
            val chNFe = "35240112345678000199550010001234561123456789"
            whenever(nfceClient.fetch(any())).thenReturn(
                NfceData(chNFe = chNFe, dhEmi = now, vNF = BigDecimal.ONE, cnpjEmitente = "", nomeEmitente = "", municipio = "", uf = "", items = emptyList()),
            )
            whenever(sameSourceDedupService.isDuplicate(any(), eq(chNFe), any())).thenReturn(true)
            whenever(importJobRepository.findByFileHash(financialAccountId, chNFe)).thenReturn(null)
            whenever(importJobRepository.save(any())).thenAnswer { it.getArgument(0) }

            val result = service.importNfce(financialAccountId, accountId, userId, "https://example.com")

            assertThat(result.status).isEqualTo(ImportJobStatus.COMPLETED)
            assertThat(result.importedCount).isEqualTo(0)
            assertThat(result.skippedCount).isEqualTo(1)
            verify(transactionRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("getJob and listJobs")
    inner class GetAndListTests {

        @Test
        fun `getJob returns job when found`() {
            val jobId = UUID.randomUUID()
            val job = ImportJob(id = jobId, financialAccountId = financialAccountId, accountId = accountId, createdByUserId = userId, source = ImportSource.OFX, fileHash = "h", status = ImportJobStatus.PENDING, createdAt = now, updatedAt = now)
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
            val list = listOf(ImportJob(id = UUID.randomUUID(), financialAccountId = financialAccountId, accountId = accountId, createdByUserId = userId, source = ImportSource.OFX, fileHash = "h", status = ImportJobStatus.COMPLETED, createdAt = now, updatedAt = now))
            whenever(importJobRepository.findByFinancialAccount(financialAccountId, accountId)).thenReturn(list)

            val result = service.listJobs(financialAccountId, accountId)

            assertThat(result).isEqualTo(list)
        }

        @Test
        fun `listJobs without financialAccountId uses findByAccount`() {
            val list = listOf(ImportJob(id = UUID.randomUUID(), financialAccountId = financialAccountId, accountId = accountId, createdByUserId = userId, source = ImportSource.OFX, fileHash = "h", status = ImportJobStatus.COMPLETED, createdAt = now, updatedAt = now))
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
            val parsed = ParsedTransaction(
                providerTransactionId = "pid-1",
                creditDebitType = CreditDebitType.DEBIT,
                amount = BigDecimal("50.00"),
                date = today,
                description = "Incoming",
                currencyCode = "BRL",
            )
            val existingTx = Transaction(
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
            val job = ImportJob(
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
                ImportProcessingResult(imported = listOf(savedIncoming), skipped = 0, duplicateCandidates = emptyList(), errors = emptyList()),
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
        fun `updates to COMPLETED only when all candidates resolved`() {
            val jobId = UUID.randomUUID()
            val parsed0 = ParsedTransaction(creditDebitType = CreditDebitType.DEBIT, amount = BigDecimal("10"), date = today, description = "A", currencyCode = "BRL")
            val parsed1 = ParsedTransaction(creditDebitType = CreditDebitType.DEBIT, amount = BigDecimal("20"), date = today, description = "B", currencyCode = "BRL")
            val existing = Transaction(id = UUID.randomUUID(), financialAccountId = financialAccountId, accountId = accountId, createdByUserId = userId, creditDebitType = CreditDebitType.DEBIT, type = TransactionType.OTHER_EXPENSE, amount = BigDecimal("10"), date = today, description = "A", source = TransactionSource.MANUAL, status = TransactionStatus.POSTED, createdAt = now, updatedAt = now)
            val candidates = listOf(
                ImportDuplicateCandidate(rowIndex = 0, parsed = parsed0, candidates = listOf(existing)),
                ImportDuplicateCandidate(rowIndex = 1, parsed = parsed1, candidates = emptyList()),
            )
            val reviewPayload = objectMapper.writeValueAsString(candidates)
            val job = ImportJob(
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
            val job = ImportJob(id = jobId, financialAccountId = financialAccountId, accountId = accountId, createdByUserId = userId, source = ImportSource.OFX, fileHash = "h", status = ImportJobStatus.PENDING, createdAt = now, updatedAt = now)
            whenever(importJobRepository.findById(jobId, accountId)).thenReturn(job)

            assertThrows<IllegalArgumentException> {
                service.resolveImportDuplicate(jobId, 0, DuplicateResolution.KEEP_BOTH, accountId)
            }
        }

        @Test
        fun `throws when candidate rowIndex not found`() {
            val jobId = UUID.randomUUID()
            val candidate = ImportDuplicateCandidate(rowIndex = 5, parsed = ParsedTransaction(creditDebitType = CreditDebitType.DEBIT, amount = BigDecimal.ONE, date = today, description = "x", currencyCode = "BRL"), candidates = emptyList())
            val job = ImportJob(id = jobId, financialAccountId = financialAccountId, accountId = accountId, createdByUserId = userId, source = ImportSource.OFX, fileHash = "h", status = ImportJobStatus.REVIEW_REQUIRED, duplicateReviewCount = 1, reviewPayload = objectMapper.writeValueAsString(listOf(candidate)), createdAt = now, updatedAt = now)
            whenever(importJobRepository.findById(jobId, accountId)).thenReturn(job)

            assertThrows<IllegalArgumentException> {
                service.resolveImportDuplicate(jobId, 0, DuplicateResolution.KEEP_EXISTING, accountId)
            }
        }
    }
}
