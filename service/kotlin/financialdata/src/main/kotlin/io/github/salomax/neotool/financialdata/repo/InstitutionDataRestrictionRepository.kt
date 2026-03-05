package io.github.salomax.neotool.financialdata.repo
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.financialdata.model.InstitutionDataRestrictionEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface InstitutionDataRestrictionRepository : JpaRepository<InstitutionDataRestrictionEntity, UUID> {
    fun findByBacenCodInstAndIsActiveTrue(bacenCodInst: String): InstitutionDataRestrictionEntity?

    fun findByBacenCodInstInAndIsActiveTrue(bacenCodInsts: List<String>): List<InstitutionDataRestrictionEntity>
}
