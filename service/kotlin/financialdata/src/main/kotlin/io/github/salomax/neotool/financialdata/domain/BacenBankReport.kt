package io.github.salomax.neotool.financialdata.domain

import io.github.salomax.neotool.financialdata.model.BacenBankReportEntity
import java.time.Instant
import java.util.UUID

data class BacenBankReport(
    val id: UUID? = null,
    val codInst: String,
    val anoMes: String,
    val nomeRelatorio: String,
    val numeroRelatorio: String,
    val tipoInstituicao: Int,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toEntity(): BacenBankReportEntity {
        return BacenBankReportEntity(
            // null for new entities, DB generates UUID v7
            id = this.id,
            codInst = this.codInst,
            anoMes = this.anoMes,
            nomeRelatorio = this.nomeRelatorio,
            numeroRelatorio = this.numeroRelatorio,
            tipoInstituicao = this.tipoInstituicao,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
