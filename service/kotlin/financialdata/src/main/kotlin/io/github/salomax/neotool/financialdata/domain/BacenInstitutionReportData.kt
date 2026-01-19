package io.github.salomax.neotool.financialdata.domain

import io.github.salomax.neotool.financialdata.model.BacenInstitutionReportDataEntity
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
) {
    fun toEntity(): BacenInstitutionReportDataEntity {
        return BacenInstitutionReportDataEntity(
            // null for new entities, DB generates UUID v7
            id = this.id,
            instReportId = this.instReportId,
            grupo = this.grupo,
            conta = this.conta,
            nomeColuna = this.nomeColuna,
            descricaoColuna = this.descricaoColuna,
            saldo = this.saldo,
            saldoAjustado = this.saldoAjustado,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
