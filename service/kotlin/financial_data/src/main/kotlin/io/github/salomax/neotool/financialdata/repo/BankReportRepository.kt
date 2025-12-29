package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.BankReportEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface BankReportRepository : JpaRepository<BankReportEntity, UUID> {
    fun findByCodInstAndAnoMesAndNomeRelatorioAndNumeroRelatorioAndTipoInstituicao(
        codInst: String,
        anoMes: String,
        nomeRelatorio: String,
        numeroRelatorio: String,
        tipoInstituicao: Int,
    ): BankReportEntity?

    fun findByCodInst(codInst: String): List<BankReportEntity>

    fun findByAnoMes(anoMes: String): List<BankReportEntity>
}
