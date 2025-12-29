package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.ParentInstitutionEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository

@Repository
interface ParentInstitutionRepository : JpaRepository<ParentInstitutionEntity, String> {
    fun findByParentCnpj(parentCnpj: String): ParentInstitutionEntity?
}
