package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.financialdata.domain.FundDailyNav
import io.github.salomax.neotool.financialdata.domain.InvestmentFund
import io.github.salomax.neotool.financialdata.domain.InvestmentFundMarketSnapshot
import io.github.salomax.neotool.financialdata.repo.InvestmentFundRepositoryCustom
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.LocalDate
import java.util.UUID

@TraceExecution
@Singleton
open class InvestmentFundSearchService(
    private val fundRepository: InvestmentFundRepositoryCustom,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Autocomplete search for investment funds by name.
     * Returns up to [limit] results ordered by trigram similarity.
     */
    fun searchFundsAutocomplete(
        query: String,
        limit: Int = 10,
    ): List<InvestmentFund> {
        if (query.isBlank()) throw IllegalArgumentException("Query cannot be empty")
        return fundRepository.searchFundsAutocomplete(query.trim(), limit)
    }

    /**
     * Paginated fund list/search with optional filters.
     * Supports Relay cursor pagination ordered by (name, id).
     */
    fun searchFunds(
        query: String?,
        assetType: String?,
        isActive: Boolean?,
        first: Int?,
        after: String?,
    ): Connection<InvestmentFund> {
        val pageSize = PaginationConstants.validatePageSize(first)

        val afterCursor = after?.let {
            try {
                CursorEncoder.decodeCompositeCursorToString(it)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }
        }

        val normalizedQuery = query?.trim()?.takeIf { it.isNotBlank() }
        val hasQuery = normalizedQuery != null

        val (results, totalCount) = fundRepository.searchFunds(
            query = normalizedQuery,
            assetType = assetType,
            isActive = isActive,
            first = pageSize + 1,
            after = afterCursor,
        )
        val hasMore = results.size > pageSize
        val page = results.take(pageSize)

        logger.debug {
            "Fund search: query=$normalizedQuery assetType=$assetType isActive=$isActive " +
                "returned=${page.size} total=$totalCount hasMore=$hasMore"
        }

        return ConnectionBuilder.buildConnection(
            items = page,
            hasMore = hasMore,
            encodeCursor = { fund ->
                // When a query is active, ordering is by sim_score DESC — include it in the
                // cursor so the next-page filter can correctly resume after the last item.
                val fieldValues = if (hasQuery) {
                    mapOf("name" to fund.name, "id" to fund.id.toString(), "simScore" to fund.simScore)
                } else {
                    mapOf("name" to fund.name, "id" to fund.id.toString())
                }
                CursorEncoder.encodeCompositeCursor(
                    fieldValues = fieldValues,
                    id = fund.id.toString(),
                )
            },
            totalCount = totalCount,
        )
    }

    /**
     * Fetch a single fund by ID including its latest NAV.
     */
    fun findFundById(id: UUID): InvestmentFund? = fundRepository.findFundById(id)

    /**
     * Fetch price history for a fund. Ordered most-recent first.
     */
    fun getPriceHistory(
        assetId: UUID,
        from: LocalDate?,
        to: LocalDate?,
        last: Int?,
    ): List<FundDailyNav> = fundRepository.getPriceHistory(assetId, from, to, last)

    fun getMarketSnapshotsByCnpj(cnpjs: Collection<String>): List<InvestmentFundMarketSnapshot> =
        fundRepository.findMarketSnapshotsByCnpj(cnpjs)
}
