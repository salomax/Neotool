package io.github.salomax.neotool.financialdata.domain

import io.github.salomax.neotool.financialdata.model.BacenBankReportDataEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class BacenBankReportData(
    val id: UUID? = null,
    val bankReportId: UUID,
    val grupo: String? = null,
    val conta: String? = null,
    val nomeColuna: String? = null,
    val descricaoColuna: String? = null,
    val saldo: BigDecimal? = null,
    val saldoAjustado: BigDecimal? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toEntity(): BacenBankReportDataEntity {
        return BacenBankReportDataEntity(
            // null for new entities, DB generates UUID v7
            id = this.id,
            bankReportId = this.bankReportId,
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
