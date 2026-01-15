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
    @ReadOnly
    override fun getLatestQuarter(): String? {
        val sql = """
            SELECT MAX(ano_mes_ref) 
            FROM financial_data.bacen_institution
        """.trimIndent()
        val nativeQuery = entityManager.createNativeQuery(sql)
        val result = nativeQuery.singleResult as? String
        return result
    }
}
