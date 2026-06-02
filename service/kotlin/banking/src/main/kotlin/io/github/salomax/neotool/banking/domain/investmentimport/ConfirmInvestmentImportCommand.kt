package io.github.salomax.neotool.banking.domain.investmentimport

import java.math.BigDecimal
import java.util.UUID

data class ConfirmInvestmentImportCommand(
    val jobId: UUID,
    val accountId: UUID,
    val userId: UUID,
    val institutionMappings: List<InstitutionAccountMapping>,
    val rowPatches: List<InvestmentImportRowPatch>,
)

data class InstitutionAccountMapping(
    val reviewInstitutionId: String,
    val financialAccountId: UUID,
)

data class InvestmentImportRowPatch(
    val rowIndex: Int,
    val principal: BigDecimal? = null,
    val conflictResolution: ConflictResolution? = null,
    val skip: Boolean = false,
)

enum class ConflictResolution {
    UPDATE,
    KEEP_EXISTING,
}
