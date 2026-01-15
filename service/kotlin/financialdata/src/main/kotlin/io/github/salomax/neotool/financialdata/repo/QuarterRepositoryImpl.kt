package io.github.salomax.neotool.financialdata.repo

import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager

/**
 * Implementation of QuarterRepositoryCustom.
 * Provides custom queries for quarter-related operations.
 */
@Singleton
open class QuarterRepositoryImpl(
    private val entityManager: EntityManager,
) : QuarterRepositoryCustom {
    /**
     * This query ensures the returned quarter is 
     * fully processed and ready for use.
     */
    @ReadOnly
    override fun getLatestQuarter(): String? {
        val sql = """
            SELECT MAX(bi.ano_mes_ref)
            FROM financial_data.bacen_institution bi
            WHERE NOT EXISTS (
                SELECT 1
                FROM financial_data.bacen_ifdata_extract_task task
                WHERE task.ano_mes = bi.ano_mes_ref
                  AND task.status NOT IN ('SUCCESS', 'EMPTY')
            )
        """.trimIndent()
        val nativeQuery = entityManager.createNativeQuery(sql)
        val result = nativeQuery.singleResult as? String
        return result
    }
}
