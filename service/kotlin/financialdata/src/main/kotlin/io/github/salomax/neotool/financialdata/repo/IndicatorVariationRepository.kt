package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.IndicatorVariationEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface IndicatorVariationRepository : JpaRepository<IndicatorVariationEntity, UUID> {
    fun findByIndicatorIdIn(indicatorIds: Collection<UUID>): List<IndicatorVariationEntity>
}
