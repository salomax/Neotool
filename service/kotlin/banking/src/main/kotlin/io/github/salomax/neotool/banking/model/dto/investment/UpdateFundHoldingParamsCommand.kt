package io.github.salomax.neotool.banking.model.dto.investment

import java.math.BigDecimal
import java.util.UUID

data class UpdateFundHoldingParamsCommand(
    val holdingId: UUID,
    val administrationFeeAnnualPercent: BigDecimal? = null,
    val hasAdministrationFeeAnnualPercent: Boolean = false,
    val comeCotasEnabled: Boolean? = null,
    val hasComeCotasEnabled: Boolean = false,
)
