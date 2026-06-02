package io.github.salomax.neotool.banking.service.investmentimport.b3

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportJob
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportJobStatus
import io.github.salomax.neotool.banking.repo.investmentimport.InvestmentImportJobRepository
import io.github.salomax.neotool.banking.service.investmentimport.InvestmentImportReviewBuilder
import io.github.salomax.neotool.banking.service.investmentimport.InvestmentImportInputStrategy
import io.github.salomax.neotool.banking.service.investmentimport.InvestmentImportSubmissionRequest
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
class B3ExcelInvestmentImportInputStrategy(
    private val parser: B3RFExcelParser,
    private val reviewBuilder: InvestmentImportReviewBuilder,
    private val repository: InvestmentImportJobRepository,
    private val objectMapper: ObjectMapper,
) : InvestmentImportInputStrategy {
    override val priority: Int = 100

    override fun supports(fileName: String): Boolean =
        fileName.endsWith(".xlsx", ignoreCase = true)

    override fun submit(request: InvestmentImportSubmissionRequest): InvestmentImportJob {
        val parseResult =
            runCatching {
                parser.parse(request.fileBytes, request.fileName)
            }.getOrElse { ex ->
                when (ex) {
                    is B3ParseException ->
                        throw ValidationException(
                            CommonErrorCode.INVALID_INPUT,
                            field = "fileBase64",
                            parameters = mapOf("reason" to ex.message.orEmpty()),
                            cause = ex,
                        )
                    else -> throw ex
                }
            }

        if (parseResult.rows.isEmpty()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileBase64",
                parameters = mapOf("reason" to "O arquivo não contém posições de Renda Fixa."),
            )
        }

        val review = reviewBuilder.build(parseResult.rows, request.accountId)
        val reviewPayloadJson = objectMapper.writeValueAsString(review)
        val extractionMetadataJson =
            objectMapper.writeValueAsString(
                mapOf(
                    "ingest_source" to "B3_EXCEL",
                    "sheet_name" to parseResult.detectedSheetName,
                    "row_count" to parseResult.rows.size,
                    "column_count" to parseResult.columnCount,
                ),
            )

        val now = Instant.now()
        return if (request.existing != null) {
            repository.update(
                request.existing.copy(
                    status = InvestmentImportJobStatus.AWAITING_REVIEW,
                    fileName = request.fileName,
                    extractionMetadata = extractionMetadataJson,
                    reviewPayload = reviewPayloadJson,
                    errorMessage = null,
                    errorDetails = null,
                    completedAt = null,
                    updatedAt = now,
                ),
            )
        } else {
            repository.save(
                InvestmentImportJob(
                    accountId = request.accountId,
                    createdByUserId = request.userId,
                    status = InvestmentImportJobStatus.AWAITING_REVIEW,
                    assetId = UUID.randomUUID(),
                    fileName = request.fileName,
                    fileHash = request.fileHash,
                    extractionMetadata = extractionMetadataJson,
                    reviewPayload = reviewPayloadJson,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
}
