package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.FinancialInstitutionRatingEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface FinancialInstitutionRatingRepository : JpaRepository<FinancialInstitutionRatingEntity, UUID> {
    fun findByFinancialInstitutionId(financialInstitutionId: UUID): List<FinancialInstitutionRatingEntity>

    fun findByFinancialInstitutionIdIn(
        financialInstitutionIds: Collection<UUID>,
    ): List<FinancialInstitutionRatingEntity>
}
