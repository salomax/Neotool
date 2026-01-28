package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.IndicatorEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface IndicatorRepository : JpaRepository<IndicatorEntity, UUID> {
    fun findByIsActiveTrueOrderByLabelAsc(): List<IndicatorEntity>
}
