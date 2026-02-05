package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.financialdata.domain.DataRestriction
import io.github.salomax.neotool.financialdata.domain.DataRestrictionReason
import io.github.salomax.neotool.financialdata.model.InstitutionDataRestrictionEntity
import io.github.salomax.neotool.financialdata.repo.InstitutionDataRestrictionRepository
import jakarta.inject.Singleton

/**
 * Service for checking whether an institution's financial data is restricted.
 * Restrictions are timeless (apply to all quarters) and managed via database.
 */
@Singleton
open class InstitutionDataRestrictionService(
    private val restrictionRepository: InstitutionDataRestrictionRepository,
) {
    /**
     * Returns restriction status for a single institution.
     * Fail-safe: on any error, returns restricted to avoid exposing data.
     */
    fun isRestricted(bacenCodInst: String): DataRestriction {
        return try {
            val entity = restrictionRepository.findByBacenCodInstAndIsActiveTrue(bacenCodInst)
            entity?.let { toDataRestriction(it) } ?: DataRestriction.NOT_RESTRICTED
        } catch (e: Exception) {
            // Fail-safe: do not expose financial data when restriction check fails
            DataRestriction(
                isRestricted = true,
                reason = DataRestrictionReason.INSTITUTION_REQUEST,
            )
        }
    }

    /**
     * Returns restriction status for multiple institutions (batch lookup).
     * Only institutions with an active restriction appear in the map; others are considered not restricted.
     */
    fun getRestrictionsForInstitutions(bacenCodInsts: List<String>): Map<String, DataRestriction> {
        if (bacenCodInsts.isEmpty()) return emptyMap()
        return try {
            val entities = restrictionRepository.findByBacenCodInstInAndIsActiveTrue(bacenCodInsts)
            entities.associate { it.bacenCodInst to toDataRestriction(it) }
        } catch (e: Exception) {
            // Fail-safe: treat all as restricted
            bacenCodInsts.associateWith {
                DataRestriction(
                    isRestricted = true,
                    reason = DataRestrictionReason.INSTITUTION_REQUEST,
                )
            }
        }
    }

    private fun toDataRestriction(entity: InstitutionDataRestrictionEntity): DataRestriction {
        val reason =
            when (entity.reason) {
                "INSTITUTION_REQUEST" -> DataRestrictionReason.INSTITUTION_REQUEST
                else -> DataRestrictionReason.INSTITUTION_REQUEST
            }
        return DataRestriction(isRestricted = true, reason = reason)
    }
}
