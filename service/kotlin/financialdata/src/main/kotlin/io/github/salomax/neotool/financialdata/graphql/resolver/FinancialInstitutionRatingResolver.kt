package io.github.salomax.neotool.financialdata.graphql.resolver

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.financialdata.domain.RatingIssuer
import io.github.salomax.neotool.financialdata.graphql.dataloader.InstitutionRatingHistoryDataLoader
import io.github.salomax.neotool.financialdata.graphql.dataloader.InstitutionRatingsDataLoader
import io.github.salomax.neotool.financialdata.graphql.dto.BacenFinanceInstitutionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InstitutionRatingDTO
import io.github.salomax.neotool.financialdata.graphql.dto.RatingHistoryDTO
import io.github.salomax.neotool.financialdata.graphql.dto.RatingIssuerDTO
import io.github.salomax.neotool.financialdata.repo.BacenInstitutionRepository
import io.github.salomax.neotool.financialdata.service.FinancialInstitutionRatingService
import io.github.salomax.neotool.financialdata.service.RatingIssuerService
import jakarta.inject.Singleton
import java.util.concurrent.CompletableFuture

@Singleton
class FinancialInstitutionRatingResolver(
    private val ratingService: FinancialInstitutionRatingService,
    private val ratingIssuerService: RatingIssuerService,
    private val bacenInstitutionRepository: BacenInstitutionRepository,
) {
    fun ratingIssuers(env: DataFetchingEnvironment): List<RatingIssuerDTO> {
        return ratingIssuerService.getEnabledIssuers().map { it.toDTO() }
    }

    fun ratings(env: DataFetchingEnvironment): CompletableFuture<List<InstitutionRatingDTO>> {
        val source =
            env.getSource<BacenFinanceInstitutionDTO>()
                ?: return CompletableFuture.completedFuture(emptyList())
        val dataLoader =
            env.getDataLoader<String, List<InstitutionRatingDTO>>(
                InstitutionRatingsDataLoader.KEY,
            )
        return dataLoader?.load(source.id) ?: CompletableFuture.completedFuture(emptyList())
    }

    fun ratingHistory(env: DataFetchingEnvironment): CompletableFuture<List<RatingHistoryDTO>> {
        val source =
            env.getSource<BacenFinanceInstitutionDTO>()
                ?: return CompletableFuture.completedFuture(emptyList())
        val limit = env.getArgument<Int>("limit") ?: 50
        val dataLoader =
            env.getDataLoader<InstitutionRatingHistoryDataLoader.RatingHistoryKey, List<RatingHistoryDTO>>(
                InstitutionRatingHistoryDataLoader.KEY,
            )
        return dataLoader
            ?.load(InstitutionRatingHistoryDataLoader.RatingHistoryKey(source.id, limit))
            ?: CompletableFuture.completedFuture(emptyList())
    }

    fun resolveRatingsBatch(bacenCodInsts: List<String>): Map<String, List<InstitutionRatingDTO>> {
        if (bacenCodInsts.isEmpty()) {
            return emptyMap()
        }
        val mappings = bacenInstitutionRepository.findFinancialInstitutionIdsByBacenCodInsts(bacenCodInsts)
        val bacenToInstitutionId = mappings.associateBy({ it.bacenCodInst }, { it.financialInstitutionId })
        val institutionIds = bacenToInstitutionId.values.distinct()
        if (institutionIds.isEmpty()) {
            return emptyMap()
        }

        val ratingsByInstitutionId = ratingService.getRatingsByInstitutionIds(institutionIds)
        val allRatings = ratingsByInstitutionId.values.flatten()
        val issuersById = ratingIssuerService.getIssuersByIds(allRatings.map { it.issuerId }.distinct())

        return bacenCodInsts.associateWith { bacenCodInst ->
            val institutionId =
                bacenToInstitutionId[bacenCodInst] ?: return@associateWith emptyList()
            val ratings = ratingsByInstitutionId[institutionId].orEmpty()
            ratings.map { rating ->
                val issuer =
                    issuersById[rating.issuerId]
                        ?: throw IllegalStateException(
                            "Issuer not found for rating ${rating.id}",
                        )
                InstitutionRatingDTO(
                    id = rating.id ?: throw IllegalStateException("Rating ID cannot be null"),
                    name = rating.name,
                    label = rating.label,
                    ratingDate = rating.ratingDate,
                    customParams = rating.customParams,
                    issuer = issuer.toDTO(),
                    updatedAt = rating.updatedAt,
                )
            }
        }
    }

    fun resolveRatingHistoryBatch(
        bacenCodInsts: List<String>,
        limit: Int,
    ): Map<String, List<RatingHistoryDTO>> {
        if (bacenCodInsts.isEmpty()) {
            return emptyMap()
        }
        val mappings = bacenInstitutionRepository.findFinancialInstitutionIdsByBacenCodInsts(bacenCodInsts)
        val bacenToInstitutionId = mappings.associateBy({ it.bacenCodInst }, { it.financialInstitutionId })
        val institutionIds = bacenToInstitutionId.values.distinct()
        if (institutionIds.isEmpty()) {
            return emptyMap()
        }

        val historyByInstitutionId = ratingService.getHistoryByInstitutionIds(institutionIds, limit)
        val allHistory = historyByInstitutionId.values.flatten()
        val issuersById = ratingIssuerService.getIssuersByIds(allHistory.map { it.issuerId }.distinct())

        return bacenCodInsts.associateWith { bacenCodInst ->
            val institutionId = bacenToInstitutionId[bacenCodInst] ?: return@associateWith emptyList()
            val histories = historyByInstitutionId[institutionId].orEmpty()
            histories.map { history ->
                val issuer = issuersById[history.issuerId]
                RatingHistoryDTO(
                    id = history.id ?: throw IllegalStateException("History ID cannot be null"),
                    name = history.name,
                    label = history.label,
                    ratingDate = history.ratingDate,
                    customParams = history.customParams,
                    issuerCode = issuer?.code ?: "UNKNOWN",
                    changedAt = history.changedAt,
                )
            }
        }
    }

    private fun RatingIssuer.toDTO() =
        RatingIssuerDTO(
            id = this.id ?: throw IllegalStateException("Issuer ID cannot be null"),
            code = this.code,
            name = this.name,
            website = this.website,
            logoAssetId = this.logoAssetId,
        )
}
