package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDate

// Autocomplete DTOs

@Introspected
@Serdeable
data class InvestmentFundAutocompleteConnectionDTO(
    val suggestions: List<InvestmentFundAutocompleteItemDTO>,
)

@Introspected
@Serdeable
data class InvestmentFundAutocompleteItemDTO(
    val id: String,
    val name: String,
    val cnpj: String,
    val assetType: String,
    val isActive: Boolean,
)

// Paginated list DTOs

@Introspected
@Serdeable
data class InvestmentFundConnectionDTO(
    val edges: List<InvestmentFundEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int?,
)

@Introspected
@Serdeable
data class InvestmentFundEdgeDTO(
    val node: InvestmentFundDTO,
    val cursor: String,
)

// Fund detail DTO (used in both list edges and detail query)

@Introspected
@Serdeable
data class InvestmentFundDTO(
    val id: String,
    val name: String,
    val cnpj: String,
    val assetType: String,
    val isActive: Boolean,
    val latestNav: FundDailyNavDTO?,
)

@Introspected
@Serdeable
data class FundDailyNavDTO(
    val date: LocalDate,
    val closePrice: Double,
    val totalNetAssets: Double?,
)
