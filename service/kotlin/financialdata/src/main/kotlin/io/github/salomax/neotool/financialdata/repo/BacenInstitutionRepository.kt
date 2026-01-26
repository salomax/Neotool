package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.BacenInstitutionEntity
import io.github.salomax.neotool.financialdata.model.BacenInstitutionId
import io.github.salomax.neotool.financialdata.repo.result.BacenInstitutionIdMapping
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository

@Repository
interface BacenInstitutionRepository : JpaRepository<BacenInstitutionEntity, BacenInstitutionId> {
    @io.micronaut.data.annotation.Query(
        "SELECT b.id.bacenCodInst AS bacenCodInst, b.financialInstitution.id AS financialInstitutionId " +
            "FROM BacenInstitutionEntity b " +
            "WHERE b.id.bacenCodInst IN (:bacenCodInsts) AND b.financialInstitution.id IS NOT NULL",
    )
    fun findFinancialInstitutionIdsByBacenCodInsts(bacenCodInsts: Collection<String>): List<BacenInstitutionIdMapping>
}
