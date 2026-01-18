package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.BacenInstitutionReportEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface BacenInstitutionReportRepository : JpaRepository<BacenInstitutionReportEntity, UUID> {
    fun findByCodInstAndAnoMesAndNumeroRelatorioAndTipoInstituicao(
        codInst: String,
        anoMes: String,
        numeroRelatorio: String,
        tipoInstituicao: Int,
    ): BacenInstitutionReportEntity?

    fun findByCodInst(codInst: String): List<BacenInstitutionReportEntity>

    fun findByAnoMes(anoMes: String): List<BacenInstitutionReportEntity>
}
