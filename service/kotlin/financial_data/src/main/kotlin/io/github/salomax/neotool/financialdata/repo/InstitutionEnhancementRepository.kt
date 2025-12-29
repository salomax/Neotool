package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.InstitutionEnhancementEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository

@Repository
interface InstitutionEnhancementRepository : JpaRepository<InstitutionEnhancementEntity, String> {
    fun findByParentCnpj(parentCnpj: String): InstitutionEnhancementEntity?
}
