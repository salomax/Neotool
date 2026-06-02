package io.github.salomax.neotool.banking.graphql.investmentimport

import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportJobStatus
import java.time.OffsetDateTime
import java.util.UUID

data class InvestmentImportJobDTO(
    val id: UUID,
    val status: InvestmentImportJobStatus,
    val fileName: String?,
    val createdAt: OffsetDateTime,
    val reviewPayload: InvestmentImportReviewDTO?,
    val errorMessage: String?,
    val errorCode: String?,
    val errorParameters: Map<String, Any?>?,
)

data class InvestmentImportReviewDTO(
    val source: String?,
    val institutions: List<InvestmentImportReviewInstitutionDTO>,
)

data class InvestmentImportReviewInstitutionDTO(
    val reviewInstitutionId: String,
    val institutionName: String,
    val matchedFinancialAccountId: UUID?,
    val matchAmbiguous: Boolean,
    val matchCandidates: List<InvestmentImportAccountCandidateDTO>,
    val rows: List<InvestmentImportReviewRowDTO>,
)

data class InvestmentImportAccountCandidateDTO(
    val financialAccountId: UUID,
    val accountName: String,
)

data class InvestmentImportReviewRowDTO(
    val rowIndex: Int,
    val productName: String,
    val assetTypeCode: String,
    val issuerName: String?,
    val providerCode: String?,
    val indexerLabel: String?,
    val issueDate: String?,
    val maturityDate: String?,
    val quantity: Double?,
    val principal: Double?,
    val currentValue: Double?,
    val requiresPrincipal: Boolean,
    val conflictingHoldingId: UUID?,
    val conflictDiff: InvestmentImportConflictDiffDTO?,
)

data class InvestmentImportConflictDiffDTO(
    val existingQuantity: Double?,
    val fileQuantity: Double?,
    val existingCurrentValue: Double?,
    val fileCurrentValue: Double?,
    val existingMaturityDate: String?,
    val fileMaturityDate: String?,
)
