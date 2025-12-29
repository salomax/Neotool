package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.InstitutionEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository

@Repository
interface InstitutionRepository : JpaRepository<InstitutionEntity, String> {
    fun findByBacenCodInst(bacenCodInst: String): InstitutionEntity?

    fun findByAnoMesRef(anoMesRef: String): List<InstitutionEntity>
}
