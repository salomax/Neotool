package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.financialdata.domain.FinancialInstitutionRating
import io.github.salomax.neotool.financialdata.repo.FinancialInstitutionRatingHistoryRepository
import io.github.salomax.neotool.financialdata.repo.FinancialInstitutionRatingRepository
import io.micronaut.data.model.Pageable
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

/**
 * Service for managing Financial Institution Ratings.
 * Handles ingestion, idempotency checks, history tracking, and evidence storage.
 */
@Singleton
open class FinancialInstitutionRatingService(
    private val ratingRepository: FinancialInstitutionRatingRepository,
    private val historyRepository: FinancialInstitutionRatingHistoryRepository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Retrieves all current ratings for a specific financial institution.
     *
     * @param financialInstitutionId The UUID of the financial institution.
     * @return List of active ratings.
     */
    @Transactional(readOnly = true)
    open fun getRatings(financialInstitutionId: UUID): List<FinancialInstitutionRating> {
        logger.debug { "Fetching ratings for institution $financialInstitutionId" }
        return ratingRepository.findByFinancialInstitutionId(financialInstitutionId)
            .map { it.toDomain() }
    }

    /**
     * Retrieves ratings for multiple institutions in a single query.
     */
    @Transactional(readOnly = true)
    open fun getRatingsByInstitutionIds(
        financialInstitutionIds: Collection<UUID>,
    ): Map<UUID, List<FinancialInstitutionRating>> {
        if (financialInstitutionIds.isEmpty()) {
            return emptyMap()
        }
        logger.debug { "Fetching ratings for institutions ${financialInstitutionIds.size}" }
        return ratingRepository.findByFinancialInstitutionIdIn(financialInstitutionIds)
            .map { it.toDomain() }
            .groupBy { it.financialInstitutionId }
    }

    /**
     * Retrieves rating history for a specific financial institution.
     *
     * @param financialInstitutionId The UUID of the financial institution.
     * @return List of rating history entries.
     */
    @Transactional(readOnly = true)
    open fun getHistory(
        financialInstitutionId: UUID,
        limit: Int = 50,
    ): List<io.github.salomax.neotool.financialdata.domain.FinancialInstitutionRatingHistory> {
        logger.debug { "Fetching rating history for institution $financialInstitutionId" }
        val pageable = Pageable.from(0, limit)
        return historyRepository.findByFinancialInstitutionIdOrderByChangedAtDesc(financialInstitutionId, pageable)
            .map { it.toDomain() }
    }

    /**
     * Retrieves rating history for multiple institutions with per-institution limits.
     */
    @Transactional(readOnly = true)
    open fun getHistoryByInstitutionIds(
        financialInstitutionIds: Collection<UUID>,
        limit: Int = 50,
    ): Map<UUID, List<io.github.salomax.neotool.financialdata.domain.FinancialInstitutionRatingHistory>> {
        if (financialInstitutionIds.isEmpty()) {
            return emptyMap()
        }
        logger.debug { "Fetching rating history for institutions ${financialInstitutionIds.size}" }
        return historyRepository.findLatestByFinancialInstitutionIdIn(financialInstitutionIds, limit)
            .map { it.toDomain() }
            .groupBy { it.financialInstitutionId }
            .mapValues { (_, items) -> items.sortedByDescending { it.changedAt } }
    }
}
