package io.github.salomax.neotool.banking.domain.investmentimport

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class InvestmentImportReview(
    val source: String? = null,
    val institutions: List<InvestmentImportReviewInstitution> = emptyList(),
)

data class InvestmentImportReviewInstitution(
    val reviewInstitutionId: String,
    val institutionName: String,
    val matchedFinancialAccountId: UUID? = null,
    val matchAmbiguous: Boolean = false,
    val matchCandidates: List<InvestmentImportAccountCandidate> = emptyList(),
    val rows: List<InvestmentImportReviewRow> = emptyList(),
)

data class InvestmentImportAccountCandidate(
    val financialAccountId: UUID,
    val accountName: String,
)

data class InvestmentImportReviewRow(
    val rowIndex: Int,
    val productName: String,
    val assetTypeCode: String,
    val issuerName: String? = null,
    val providerCode: String? = null,
    val indexerLabel: String? = null,
    val issueDate: LocalDate? = null,
    val maturityDate: LocalDate? = null,
    val quantity: BigDecimal? = null,
    val principal: BigDecimal? = null,
    val currentValue: BigDecimal? = null,
    val requiresPrincipal: Boolean = false,
    val conflictingHoldingId: UUID? = null,
    val conflictDiff: InvestmentImportConflictDiff? = null,
)

data class InvestmentImportConflictDiff(
    val existingQuantity: BigDecimal? = null,
    val fileQuantity: BigDecimal? = null,
    val existingCurrentValue: BigDecimal? = null,
    val fileCurrentValue: BigDecimal? = null,
    val existingMaturityDate: LocalDate? = null,
    val fileMaturityDate: LocalDate? = null,
)
