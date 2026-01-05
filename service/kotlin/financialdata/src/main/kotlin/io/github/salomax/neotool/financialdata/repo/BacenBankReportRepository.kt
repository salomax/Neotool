package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.BacenBankReportEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface BacenBankReportRepository : JpaRepository<BacenBankReportEntity, UUID> {
    fun findByCodInstAndAnoMesAndNomeRelatorioAndNumeroRelatorioAndTipoInstituicao(
        codInst: String,
        anoMes: String,
        nomeRelatorio: String,
        numeroRelatorio: String,
        tipoInstituicao: Int,
    ): BacenBankReportEntity?

    fun findByCodInst(codInst: String): List<BacenBankReportEntity>

    fun findByAnoMes(anoMes: String): List<BacenBankReportEntity>
}

