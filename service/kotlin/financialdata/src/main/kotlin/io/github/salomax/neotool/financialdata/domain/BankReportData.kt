package io.github.salomax.neotool.financialdata.domain

import io.github.salomax.neotool.financialdata.model.BankReportDataEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class BankReportData(
    val id: UUID? = null,
    val bankReportId: UUID,
    val grupo: String? = null,
    val conta: String? = null,
    val nomeColuna: String? = null,
    val descricaoColuna: String? = null,
    val saldo: BigDecimal? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toEntity(): BankReportDataEntity {
        return BankReportDataEntity(
            // null for new entities, DB generates UUID v7
            id = this.id,
            bankReportId = this.bankReportId,
            grupo = this.grupo,
            conta = this.conta,
            nomeColuna = this.nomeColuna,
            descricaoColuna = this.descricaoColuna,
            saldo = this.saldo,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
