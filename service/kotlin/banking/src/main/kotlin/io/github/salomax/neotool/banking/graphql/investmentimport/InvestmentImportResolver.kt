package io.github.salomax.neotool.banking.graphql.investmentimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.investmentimport.ConfirmInvestmentImportCommand
import io.github.salomax.neotool.banking.domain.investmentimport.ConflictResolution
import io.github.salomax.neotool.banking.domain.investmentimport.InstitutionAccountMapping
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportAccountCandidate
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportConflictDiff
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportJob
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportJobStatus
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportReview
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportReviewInstitution
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportReviewRow
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportRowPatch
import io.github.salomax.neotool.banking.service.investmentimport.InvestmentImportService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

@Singleton
class InvestmentImportResolver(
    private val investmentImportService: InvestmentImportService,
    private val objectMapper: ObjectMapper,
) {
    fun submitInvestmentImport(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): InvestmentImportJobDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }

        val fileName =
            input["fileName"] as? String ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileName",
                parameters = mapOf("reason" to "fileName is required"),
            )

        val fileBase64 =
            input["fileBase64"] as? String ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileBase64",
                parameters = mapOf("reason" to "fileBase64 is required"),
            )

        val fileBytes =
            try {
                Base64.getDecoder().decode(fileBase64)
            } catch (e: IllegalArgumentException) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "fileBase64",
                    parameters = mapOf("reason" to "Invalid base64"),
                    cause = e,
                )
            }

        return investmentImportService.submitInvestmentImport(
            accountId = accountId,
            userId = userId,
            fileName = fileName,
            fileBytes = fileBytes,
        ).toDTO()
    }

    fun confirmInvestmentImport(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): InvestmentImportJobDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }

        val jobId = parseUuid(input["jobId"] as? String, "jobId")
        val institutionMappingsInput = asMapList(input["institutionMappings"], "institutionMappings")
        val rowPatchesInput = asMapList(input["rowPatches"], "rowPatches")

        val command =
            ConfirmInvestmentImportCommand(
                jobId = jobId,
                accountId = accountId,
                userId = userId,
                institutionMappings =
                    institutionMappingsInput.mapIndexed { idx, item ->
                        InstitutionAccountMapping(
                            reviewInstitutionId =
                                item["reviewInstitutionId"] as? String ?: throw ValidationException(
                                    CommonErrorCode.INVALID_INPUT,
                                    field = "institutionMappings[$idx].reviewInstitutionId",
                                    parameters = mapOf("reason" to "reviewInstitutionId is required"),
                                ),
                            financialAccountId =
                                parseUuid(
                                    item["financialAccountId"] as? String,
                                    "institutionMappings[$idx].financialAccountId",
                                ),
                        )
                    },
                rowPatches =
                    rowPatchesInput.mapIndexed { idx, item ->
                        val rowIndex =
                            (item["rowIndex"] as? Number)?.toInt() ?: throw ValidationException(
                                CommonErrorCode.INVALID_INPUT,
                                field = "rowPatches[$idx].rowIndex",
                                parameters = mapOf("reason" to "rowIndex is required"),
                            )
                        val principalValue = (item["principal"] as? Number)?.let { BigDecimal(it.toString()) }
                        val conflictResolution =
                            (item["conflictResolution"] as? String)?.let { value ->
                                runCatching { ConflictResolution.valueOf(value) }.getOrElse {
                                    throw ValidationException(
                                        CommonErrorCode.INVALID_INPUT,
                                        field = "rowPatches[$idx].conflictResolution",
                                        parameters = mapOf("reason" to "Invalid conflictResolution"),
                                    )
                                }
                            }
                        val skip = item["skip"] as? Boolean ?: false
                        InvestmentImportRowPatch(
                            rowIndex = rowIndex,
                            principal = principalValue,
                            conflictResolution = conflictResolution,
                            skip = skip,
                        )
                    },
            )

        return investmentImportService.confirmInvestmentImport(command).toDTO()
    }

    fun investmentImportJob(
        id: String,
        principal: RequestPrincipal,
    ): InvestmentImportJobDTO? {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return investmentImportService.getJob(UUID.fromString(id), accountId)?.toDTO()
    }

    private fun InvestmentImportJob.toDTO(): InvestmentImportJobDTO {
        val errorInfo = resolveErrorInfo()
        return InvestmentImportJobDTO(
            id = id ?: throw IllegalStateException("InvestmentImportJob id is required"),
            status = status,
            fileName = fileName,
            createdAt = createdAt.atOffset(ZoneOffset.UTC),
            reviewPayload = parseReviewPayloadForGraphQL(this),
            errorMessage = errorMessage,
            errorCode = errorInfo.code,
            errorParameters = errorInfo.parameters,
        )
    }

    private fun parseReviewPayloadForGraphQL(job: InvestmentImportJob): InvestmentImportReviewDTO? {
        if (job.status != InvestmentImportJobStatus.AWAITING_REVIEW) return null
        val payload = job.reviewPayload ?: return null
        val parsed = runCatching { objectMapper.readValue(payload, InvestmentImportReview::class.java) }.getOrNull() ?: return null
        return parsed.toDTO()
    }

    @Suppress("UNCHECKED_CAST")
    private fun InvestmentImportJob.resolveErrorInfo(): JobErrorInfo {
        val detailsMap =
            errorDetails?.let { detailsJson ->
                runCatching { objectMapper.readValue(detailsJson, Map::class.java) as? Map<String, Any?> }.getOrNull()
            }

        val explicitCode = detailsMap?.get("errorCode")?.toString()
        val explicitParams = detailsMap?.get("errorParameters") as? Map<String, Any?>

        if (!explicitCode.isNullOrBlank()) {
            return JobErrorInfo(code = explicitCode, parameters = explicitParams)
        }

        return if (status == InvestmentImportJobStatus.FAILED) {
            JobErrorInfo(
                code = CommonErrorCode.INTERNAL_ERROR.code,
                parameters = errorMessage?.let { mapOf("reason" to it) },
            )
        } else {
            JobErrorInfo(code = null, parameters = null)
        }
    }

    private fun InvestmentImportReview.toDTO(): InvestmentImportReviewDTO =
        InvestmentImportReviewDTO(
            source = source,
            institutions = institutions.map { it.toDTO() },
        )

    private fun InvestmentImportReviewInstitution.toDTO(): InvestmentImportReviewInstitutionDTO =
        InvestmentImportReviewInstitutionDTO(
            reviewInstitutionId = reviewInstitutionId,
            institutionName = institutionName,
            matchedFinancialAccountId = matchedFinancialAccountId,
            matchAmbiguous = matchAmbiguous,
            matchCandidates = matchCandidates.map { it.toDTO() },
            rows = rows.map { it.toDTO() },
        )

    private fun InvestmentImportAccountCandidate.toDTO(): InvestmentImportAccountCandidateDTO =
        InvestmentImportAccountCandidateDTO(
            financialAccountId = financialAccountId,
            accountName = accountName,
        )

    private fun InvestmentImportReviewRow.toDTO(): InvestmentImportReviewRowDTO =
        InvestmentImportReviewRowDTO(
            rowIndex = rowIndex,
            productName = productName,
            assetTypeCode = assetTypeCode,
            issuerName = issuerName,
            providerCode = providerCode,
            indexerLabel = indexerLabel,
            issueDate = issueDate?.toString(),
            maturityDate = maturityDate?.toString(),
            quantity = quantity?.toDouble(),
            principal = principal?.toDouble(),
            currentValue = currentValue?.toDouble(),
            requiresPrincipal = requiresPrincipal,
            conflictingHoldingId = conflictingHoldingId,
            conflictDiff = conflictDiff?.toDTO(),
        )

    private fun InvestmentImportConflictDiff.toDTO(): InvestmentImportConflictDiffDTO =
        InvestmentImportConflictDiffDTO(
            existingQuantity = existingQuantity?.toDouble(),
            fileQuantity = fileQuantity?.toDouble(),
            existingCurrentValue = existingCurrentValue?.toDouble(),
            fileCurrentValue = fileCurrentValue?.toDouble(),
            existingMaturityDate = existingMaturityDate?.toString(),
            fileMaturityDate = fileMaturityDate?.toString(),
        )

    private fun parseUuid(
        value: String?,
        field: String,
    ): UUID {
        if (value.isNullOrBlank()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "$field is required"),
            )
        }

        return try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "Invalid UUID"),
                cause = e,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun asMapList(
        value: Any?,
        field: String,
    ): List<Map<String, Any?>> {
        val list =
            value as? List<*> ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "$field must be a list"),
            )

        return list.mapIndexed { index, item ->
            item as? Map<String, Any?> ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "$field[$index]",
                parameters = mapOf("reason" to "$field[$index] must be an object"),
            )
        }
    }

    private data class JobErrorInfo(
        val code: String?,
        val parameters: Map<String, Any?>?,
    )
}
