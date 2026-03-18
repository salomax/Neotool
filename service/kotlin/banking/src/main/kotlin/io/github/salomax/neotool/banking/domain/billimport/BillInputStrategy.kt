package io.github.salomax.neotool.banking.domain.billimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutputMapper
import io.github.salomax.neotool.banking.domain.import.ImportTextDecoder
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.import.excel.ExcelExtractionService
import io.github.salomax.neotool.banking.domain.import.excel.reader.ExcelDeterministicRoutingService
import io.github.salomax.neotool.banking.domain.import.fingerprint.FingerprintFileType
import io.github.salomax.neotool.banking.domain.import.parser.CsvNubankParser
import io.github.salomax.neotool.banking.domain.import.parser.OFXParser
import io.github.salomax.neotool.banking.domain.import.parser.OfxDocumentFormat
import io.github.salomax.neotool.banking.domain.import.parser.OfxStatementIntent
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.infra.assets.BillImportAssetStorage
import io.github.salomax.neotool.banking.infra.extractor.pdf.PdfExtractorSubmittedEvent
import io.github.salomax.neotool.banking.infra.extractor.pdf.PdfExtractorSubmittedProducer
import io.github.salomax.neotool.banking.infra.kafka.ImportCallbackContext
import io.github.salomax.neotool.common.error.BusinessRuleException
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

internal data class BillImportSubmissionRequest(
    val accountId: UUID,
    val userId: UUID,
    val importType: ImportDocumentType,
    val financialAccountId: UUID?,
    val fileName: String,
    val fileHash: String,
    val fileBytes: ByteArray,
    val password: String?,
    val existing: BillImportJob?,
)

internal interface BillInputStrategy {
    val priority: Int

    fun supports(
        fileName: String,
        importType: ImportDocumentType,
    ): Boolean

    fun submit(request: BillImportSubmissionRequest): BillImportJob
}

internal class DeterministicOfxBillInputStrategy(
    private val repository: BillImportJobRepository,
    private val objectMapper: ObjectMapper,
) : BillInputStrategy {
    override val priority: Int = 100

    override fun supports(
        fileName: String,
        importType: ImportDocumentType,
    ): Boolean = importType == ImportDocumentType.CREDIT_CARD_BILL && fileName.endsWith(".ofx", ignoreCase = true)

    override fun submit(request: BillImportSubmissionRequest): BillImportJob {
        val parseResult = OFXParser.parseWithIntent(request.fileBytes)
        if (parseResult.format == OfxDocumentFormat.UNKNOWN) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileBase64",
                parameters = mapOf("reason" to "File is not a valid OFX document"),
            )
        }
        if (parseResult.statementIntent != OfxStatementIntent.CREDIT_CARD_STATEMENT) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileName",
                parameters = mapOf("reason" to "OFX file must be a credit card statement for bill import"),
            )
        }
        if (parseResult.rows.isEmpty()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileBase64",
                parameters = mapOf("reason" to "OFX file has no transactions to review"),
            )
        }

        val detectedLastFourDigits = extractOfxCardLastFourDigits(parseResult.accountIdentifier)
        val now = Instant.now()
        val reviewPayloadJson =
            objectMapper.writeValueAsString(
                buildSingleCardBillReview(
                    rows = parseResult.rows,
                    lastFourDigits = detectedLastFourDigits,
                    statementFrom = parseResult.statementFrom,
                    statementTo = parseResult.statementTo,
                    reviewMode =
                        if (detectedLastFourDigits != null) {
                            BillImportReviewMode.CARD_BUCKET_MATCHING
                        } else {
                            BillImportReviewMode.TX_CARD_ASSIGNMENT
                        },
                ),
            )
        val extractionMetadata =
            objectMapper.writeValueAsString(
                mapOf(
                    "ingest_source" to "OFX",
                    "statement_intent" to parseResult.statementIntent.name,
                    "document_format" to parseResult.format.name,
                ),
            )

        return if (request.existing != null) {
            repository.update(
                request.existing.copy(
                    importType = request.importType,
                    financialAccountId = request.financialAccountId,
                    status = BillImportJobStatus.AWAITING_REVIEW,
                    fileName = request.fileName,
                    extractedText = null,
                    extractionMetadata = extractionMetadata,
                    reviewPayload = reviewPayloadJson,
                    errorMessage = null,
                    errorDetails = null,
                    completedAt = null,
                    updatedAt = now,
                ),
            )
        } else {
            repository.save(
                BillImportJob(
                    importType = request.importType,
                    financialAccountId = request.financialAccountId,
                    accountId = request.accountId,
                    createdByUserId = request.userId,
                    status = BillImportJobStatus.AWAITING_REVIEW,
                    assetId = UUID.randomUUID(),
                    fileName = request.fileName,
                    fileHash = request.fileHash,
                    extractionMetadata = extractionMetadata,
                    reviewPayload = reviewPayloadJson,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
}

internal class CsvBillInputStrategy(
    private val repository: BillImportJobRepository,
    private val objectMapper: ObjectMapper,
) : BillInputStrategy {
    override val priority: Int = 90

    override fun supports(
        fileName: String,
        importType: ImportDocumentType,
    ): Boolean = importType == ImportDocumentType.CREDIT_CARD_BILL && fileName.endsWith(".csv", ignoreCase = true)

    override fun submit(request: BillImportSubmissionRequest): BillImportJob {
        if (!isNubankCreditCardCsv(request.fileBytes)) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileName",
                parameters = mapOf("reason" to "CSV bill import currently supports Nubank credit card CSV only"),
            )
        }

        val rows = CsvNubankParser.parse(request.fileBytes)
        if (rows.isEmpty()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileBase64",
                parameters = mapOf("reason" to "CSV file has no transactions to review"),
            )
        }

        val now = Instant.now()
        val reviewPayloadJson = objectMapper.writeValueAsString(buildSingleCardBillReview(rows))
        val extractionMetadata =
            objectMapper.writeValueAsString(
                mapOf(
                    "ingest_source" to "CSV",
                    "reader_id" to CsvNubankParser.id(),
                    "layout" to "nubank_credit_card",
                ),
            )

        return saveAwaitingReviewJob(
            repository = repository,
            request = request,
            now = now,
            reviewPayloadJson = reviewPayloadJson,
            extractionMetadata = extractionMetadata,
        )
    }

    private fun isNubankCreditCardCsv(bytes: ByteArray): Boolean {
        val firstNonEmptyLine =
            ImportTextDecoder.decode(bytes).text
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                ?.removePrefix("\uFEFF")
                ?.lowercase()
        return firstNonEmptyLine == "date,transaction_type,title,amount,category,charges,identificador"
    }
}

internal class ExcelBillInputStrategy(
    private val repository: BillImportJobRepository,
    private val objectMapper: ObjectMapper,
    private val excelExtractionService: ExcelExtractionService = ExcelExtractionService(),
    private val excelDeterministicRoutingService: ExcelDeterministicRoutingService = ExcelDeterministicRoutingService(),
) : BillInputStrategy {
    override val priority: Int = 80

    override fun supports(
        fileName: String,
        importType: ImportDocumentType,
    ): Boolean =
        importType == ImportDocumentType.CREDIT_CARD_BILL &&
            (fileName.endsWith(".xlsx", ignoreCase = true) || fileName.endsWith(".xls", ignoreCase = true))

    override fun submit(request: BillImportSubmissionRequest): BillImportJob {
        val extraction =
            runCatching {
                excelExtractionService.extract(
                    bytes = request.fileBytes,
                    fileName = request.fileName,
                )
            }.getOrElse { ex ->
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "fileBase64",
                    parameters = mapOf("reason" to "File is not a valid Excel workbook"),
                    cause = ex,
                )
            }
        val extractionMetadata = extraction.toExtractionMetadataMap()
        val deterministicOutput =
            excelDeterministicRoutingService.route(
                fileType = resolveExcelFileType(extractionMetadata),
                documentType = "BANK_STATEMENT",
                extractedText = extraction.extractedText,
                extractionMetadata = extractionMetadata,
            ) ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileName",
                parameters = mapOf("reason" to "Excel bill import requires a supported tabular layout"),
            )

        val rows = CanonicalReaderOutputMapper.toParsedTransactions(deterministicOutput)
        if (rows.isEmpty()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileBase64",
                parameters = mapOf("reason" to "Excel file has no transactions to review"),
            )
        }

        val now = Instant.now()
        val reviewPayloadJson = objectMapper.writeValueAsString(buildSingleCardBillReview(rows))
        val metadataJson =
            objectMapper.writeValueAsString(
                extractionMetadata +
                    mapOf(
                        "ingest_source" to "EXCEL",
                        "reader_id" to deterministicOutput.readerId,
                    ),
            )

        return saveAwaitingReviewJob(
            repository = repository,
            request = request,
            now = now,
            reviewPayloadJson = reviewPayloadJson,
            extractionMetadata = metadataJson,
        )
    }

    private fun resolveExcelFileType(extractionMetadata: Map<String, Any?>): FingerprintFileType =
        if (extractionMetadata["workbook_type"]?.toString()?.uppercase() == "XLS") {
            FingerprintFileType.XLS
        } else {
            FingerprintFileType.XLSX
        }
}

private fun buildSingleCardBillReview(
    rows: List<ParsedTransaction>,
    lastFourDigits: String? = null,
    statementFrom: java.time.LocalDate? = null,
    statementTo: java.time.LocalDate? = null,
    reviewMode: BillImportReviewMode = BillImportReviewMode.CARD_BUCKET_MATCHING,
): BillImportReview {
    val transactions =
        rows.map { row -> BillTransactionEnricher.toBillReviewTransaction(row) }
    val computedTotal = computeSignedTotal(transactions)
    return BillImportReview(
        reviewMode = reviewMode,
        statementPeriod =
            DateRange(
                from = statementFrom ?: rows.minOfOrNull { it.date },
                to = statementTo ?: rows.maxOfOrNull { it.date },
            ),
        cards =
            listOf(
                BillImportReviewCard(
                    reviewCardId = "card-1",
                    lastFourDigits = lastFourDigits,
                    closingDate = statementTo ?: rows.maxOfOrNull { it.date },
                    declaredTotal = computedTotal,
                    computedTotal = computedTotal,
                    totalMatchesDeclared = true,
                    transactions = transactions.sortedWith(compareBy({ it.date }, { it.description })),
                ),
            ),
    )
}

private fun computeSignedTotal(transactions: List<BillImportReviewTransaction>): BigDecimal =
    transactions.fold(BigDecimal.ZERO) { acc, tx ->
        if (tx.creditDebitType == CreditDebitType.CREDIT) {
            acc.subtract(tx.amount)
        } else {
            acc.add(tx.amount)
        }
    }

private fun extractOfxCardLastFourDigits(accountIdentifier: String?): String? {
    val raw = accountIdentifier?.trim().orEmpty()
    if (raw.isBlank()) return null

    val digits = raw.filter(Char::isDigit)
    if (digits.length < 4) return null

    val hasMaskPrefix = raw.contains('*') || raw.contains('x', ignoreCase = true) || raw.contains('•')
    if (hasMaskPrefix) {
        return digits.takeLast(4)
    }

    val bareDigitsOnly = raw.all { it.isDigit() }
    if (bareDigitsOnly) {
        // A short bare account id like "1181" is not reliable evidence of card last four digits.
        return if (digits.length >= 12) digits.takeLast(4) else null
    }

    // Allow formatted PAN-like values with separators, but avoid free-form identifiers.
    val separatorsOnly = raw.all { it.isDigit() || it == ' ' || it == '-' }
    return if (separatorsOnly && digits.length >= 12) digits.takeLast(4) else null
}

private fun saveAwaitingReviewJob(
    repository: BillImportJobRepository,
    request: BillImportSubmissionRequest,
    now: Instant,
    reviewPayloadJson: String,
    extractionMetadata: String,
): BillImportJob =
    if (request.existing != null) {
        repository.update(
            request.existing.copy(
                importType = request.importType,
                financialAccountId = request.financialAccountId,
                status = BillImportJobStatus.AWAITING_REVIEW,
                fileName = request.fileName,
                extractedText = null,
                extractionMetadata = extractionMetadata,
                reviewPayload = reviewPayloadJson,
                errorMessage = null,
                errorDetails = null,
                completedAt = null,
                updatedAt = now,
            ),
        )
    } else {
        repository.save(
            BillImportJob(
                importType = request.importType,
                financialAccountId = request.financialAccountId,
                accountId = request.accountId,
                createdByUserId = request.userId,
                status = BillImportJobStatus.AWAITING_REVIEW,
                assetId = UUID.randomUUID(),
                fileName = request.fileName,
                fileHash = request.fileHash,
                extractionMetadata = extractionMetadata,
                reviewPayload = reviewPayloadJson,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

internal class AsyncPdfBillInputStrategy(
    private val repository: BillImportJobRepository,
    private val billAssetStorage: BillImportAssetStorage,
    private val submittedProducer: PdfExtractorSubmittedProducer?,
) : BillInputStrategy {
    override val priority: Int = 0

    override fun supports(
        fileName: String,
        importType: ImportDocumentType,
    ): Boolean = fileName.endsWith(".pdf", ignoreCase = true)

    override fun submit(request: BillImportSubmissionRequest): BillImportJob {
        val producer =
            submittedProducer ?: throw BusinessRuleException(
                errorCode = io.github.salomax.neotool.banking.error.BankingErrorCode.IMPORT_ASYNC_PIPELINE_UNAVAILABLE,
                parameters = mapOf("kafkaEnabled" to false, "pipeline" to "pdf"),
            )

        val uploadedAsset =
            billAssetStorage.uploadPdf(
                accountId = request.accountId,
                fileName = request.fileName,
                bytes = request.fileBytes,
            )

        val now = Instant.now()
        val job =
            if (request.existing != null) {
                repository.update(
                    request.existing.copy(
                        importType = request.importType,
                        financialAccountId = request.financialAccountId,
                        status = BillImportJobStatus.PENDING,
                        assetId = uploadedAsset.assetId,
                        fileName = request.fileName,
                        extractedText = null,
                        extractionMetadata = null,
                        reviewPayload = null,
                        errorMessage = null,
                        errorDetails = null,
                        completedAt = null,
                        updatedAt = now,
                    ),
                )
            } else {
                repository.save(
                    BillImportJob(
                        importType = request.importType,
                        financialAccountId = request.financialAccountId,
                        accountId = request.accountId,
                        createdByUserId = request.userId,
                        status = BillImportJobStatus.PENDING,
                        assetId = uploadedAsset.assetId,
                        fileName = request.fileName,
                        fileHash = request.fileHash,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

        val savedJobId = requireNotNull(job.id) { "BillImportJob id is required after save" }
        producer.publish(
            PdfExtractorSubmittedEvent(
                job_id = savedJobId,
                asset_id = uploadedAsset.assetId,
                download_url = uploadedAsset.downloadUrl,
                password = request.password?.takeIf { it.isNotBlank() },
                document_type = request.importType.name,
                callback_context =
                    ImportCallbackContext(
                        job_id = savedJobId,
                        account_id = request.accountId,
                        user_id = request.userId,
                    ),
            ),
        )

        return job
    }
}
