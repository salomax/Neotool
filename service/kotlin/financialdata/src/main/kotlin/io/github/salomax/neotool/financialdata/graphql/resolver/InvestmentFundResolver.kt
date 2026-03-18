package io.github.salomax.neotool.financialdata.graphql.resolver

import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.financialdata.graphql.dto.FundDailyNavDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.InvestmentFundMapper
import io.github.salomax.neotool.financialdata.service.InvestmentFundSearchService
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.LocalDate
import java.util.UUID

/**
 * GraphQL resolver for investment fund queries.
 */
@Singleton
class InvestmentFundResolver(
    private val fundSearchService: InvestmentFundSearchService,
    private val mapper: InvestmentFundMapper,
) {
    private val logger = KotlinLogging.logger {}

    fun searchInvestmentFundsAutocomplete(query: String): InvestmentFundAutocompleteConnectionDTO {
        if (query.isBlank()) throw IllegalArgumentException("Query cannot be empty")
        val funds = fundSearchService.searchFundsAutocomplete(query)
        return mapper.toAutocompleteConnectionDTO(funds)
    }

    fun investmentFunds(
        query: String?,
        assetType: String?,
        isActive: Boolean?,
        first: Int?,
        after: String?,
    ): InvestmentFundConnectionDTO {
        val pageSize = PaginationConstants.validatePageSize(first)
        val connection = fundSearchService.searchFunds(query, assetType, isActive, pageSize, after)
        return mapper.toConnectionDTO(connection)
    }

    fun investmentFund(id: String): InvestmentFundDTO? {
        val uuid = try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid fund id: $id")
        }
        val fund = fundSearchService.findFundById(uuid) ?: return null
        return mapper.toFundDTO(fund)
    }

    fun priceHistory(
        fundId: String,
        from: LocalDate?,
        to: LocalDate?,
        last: Int?,
    ): List<FundDailyNavDTO> {
        val uuid = UUID.fromString(fundId)
        val history = fundSearchService.getPriceHistory(uuid, from, to, last)
        return mapper.toNavListDTO(history)
    }
}
