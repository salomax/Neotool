    package io.github.salomax.neotool.financialdata.service

import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager

/**
 * Service for quarter-related operations in the financial_data module.
 * Provides shared functionality for determining latest quarters.
 */
@Singleton
open class QuarterService(
    private val entityManager: EntityManager,
) {
    /**
     * Gets the latest quarter (max ano_mes_ref) globally across all institutions.
     * Used as default when anoMes parameter is not provided.
     *
     * @return Latest quarter in "YYYYMM" format or null if no data exists
     */
    @ReadOnly
    open fun getLatestQuarter(): String? {
        val sql = """
            SELECT MAX(ano_mes_ref) 
            FROM financial_data.bacen_institution
        """.trimIndent()
        val nativeQuery = entityManager.createNativeQuery(sql)
        val result = nativeQuery.singleResult as? String
        return result
    }
}
