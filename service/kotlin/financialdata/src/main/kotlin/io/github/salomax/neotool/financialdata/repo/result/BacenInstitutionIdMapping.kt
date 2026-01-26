package io.github.salomax.neotool.financialdata.repo.result

import java.util.UUID

/**
 * Maps Bacen institution codes to financial institution IDs.
 */
data class BacenInstitutionIdMapping(
    val bacenCodInst: String,
    val financialInstitutionId: UUID,
)
