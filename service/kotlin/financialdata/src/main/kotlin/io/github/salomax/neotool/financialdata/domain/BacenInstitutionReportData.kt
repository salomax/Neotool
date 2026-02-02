package io.github.salomax.neotool.financialdata.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class BacenInstitutionReportData(
    val id: UUID? = null,
    val instReportId: UUID,
    val grupo: String? = null,
    val conta: String? = null,
    val nomeColuna: String? = null,
    val descricaoColuna: String? = null,
    val saldo: BigDecimal? = null,
    val saldoAjustado: BigDecimal? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
