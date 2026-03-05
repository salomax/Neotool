package io.github.salomax.neotool.banking.graphql.resolver

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportJob
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportJobStatus
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportService
import io.github.salomax.neotool.banking.graphql.dto.PdfImportJobDTO
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.util.Base64
import java.util.UUID

@Singleton
class PdfImportResolver(
    private val pdfImportService: PdfImportService,
    private val objectMapper: ObjectMapper,
) {

    fun submitPdfBillImport(input: Map<String, Any?>, principal: RequestPrincipal): PdfImportJobDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }

        val fileName = input["fileName"] as? String ?: throw ValidationException(
            CommonErrorCode.INVALID_INPUT,
            field = "fileName",
            parameters = mapOf("reason" to "fileName is required"),
        )
        val fileBase64 = input["fileBase64"] as? String ?: throw ValidationException(
            CommonErrorCode.INVALID_INPUT,
            field = "fileBase64",
            parameters = mapOf("reason" to "fileBase64 is required"),
        )
        val financialAccountId = (input["financialAccountId"] as? String)?.let { UUID.fromString(it) }
        val fileBytes = try {
            Base64.getDecoder().decode(fileBase64)
        } catch (e: IllegalArgumentException) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileBase64",
                parameters = mapOf("reason" to "Invalid base64"),
                cause = e,
            )
        }

        val job = pdfImportService.submitPdfBillImport(
            accountId = accountId,
            userId = userId,
            financialAccountId = financialAccountId,
            fileName = fileName,
            fileBytes = fileBytes,
        )
        return job.toDTO()
    }

    fun pdfImportJob(jobId: String, principal: RequestPrincipal): PdfImportJobDTO? {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val job = pdfImportService.getJob(UUID.fromString(jobId), accountId) ?: return null
        return job.toDTO()
    }

    fun pdfImportJobs(financialAccountId: String?, principal: RequestPrincipal): List<PdfImportJobDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val faId = financialAccountId?.let { UUID.fromString(it) }
        return pdfImportService.listJobs(faId, accountId).map { it.toDTO() }
    }

    private fun PdfImportJob.toDTO(): PdfImportJobDTO =
        PdfImportJobDTO(
            id = id ?: throw IllegalStateException("PdfImportJob id is required"),
            status = status,
            fileName = fileName,
            createdAt = createdAt,
            reviewPayload = parseReviewPayloadForGraphQL(this),
            errorMessage = errorMessage,
        )

    private fun parseReviewPayloadForGraphQL(job: PdfImportJob): Any? {
        if (job.status != PdfImportJobStatus.AWAITING_REVIEW) {
            return null
        }
        val payload = job.reviewPayload ?: return null
        return runCatching { objectMapper.readValue(payload, Any::class.java) }.getOrNull()
    }
}

