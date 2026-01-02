package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.FinancialInstitutionEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository

@Repository
interface FinancialInstitutionRepository : JpaRepository<FinancialInstitutionEntity, String> {
    fun findByParentCnpj(parentCnpj: String): FinancialInstitutionEntity?
}

