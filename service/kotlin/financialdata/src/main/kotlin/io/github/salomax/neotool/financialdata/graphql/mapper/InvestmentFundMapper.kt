package io.github.salomax.neotool.financialdata.graphql.mapper

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.financialdata.domain.FundDailyNav
import io.github.salomax.neotool.financialdata.domain.InvestmentFund
import io.github.salomax.neotool.financialdata.domain.InvestmentFundMarketSnapshot
import io.github.salomax.neotool.financialdata.graphql.dto.FundDailyNavDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundAutocompleteItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundMarketSnapshotDTO
import io.github.salomax.neotool.financialdata.graphql.dto.PageInfoDTO
import jakarta.inject.Singleton

@Singleton
class InvestmentFundMapper {
    fun toAutocompleteConnectionDTO(funds: List<InvestmentFund>): InvestmentFundAutocompleteConnectionDTO =
        InvestmentFundAutocompleteConnectionDTO(
            suggestions = funds.map { toAutocompleteItemDTO(it) },
        )

    fun toAutocompleteItemDTO(fund: InvestmentFund): InvestmentFundAutocompleteItemDTO =
        InvestmentFundAutocompleteItemDTO(
            id = fund.id.toString(),
            name = fund.name,
            cnpj = fund.cnpj,
            assetType = fund.assetType,
            isActive = fund.isActive,
        )

    fun toConnectionDTO(connection: Connection<InvestmentFund>): InvestmentFundConnectionDTO =
        InvestmentFundConnectionDTO(
            edges =
                connection.edges.map { edge ->
                    InvestmentFundEdgeDTO(
                        node = toFundDTO(edge.node),
                        cursor = edge.cursor,
                    )
                },
            pageInfo =
                PageInfoDTO(
                    hasNextPage = connection.pageInfo.hasNextPage,
                    hasPreviousPage = connection.pageInfo.hasPreviousPage,
                    startCursor = connection.pageInfo.startCursor,
                    endCursor = connection.pageInfo.endCursor,
                ),
            totalCount = connection.totalCount?.toInt(),
        )

    fun toFundDTO(fund: InvestmentFund): InvestmentFundDTO =
        InvestmentFundDTO(
            id = fund.id.toString(),
            name = fund.name,
            cnpj = fund.cnpj,
            assetType = fund.assetType,
            isActive = fund.isActive,
            latestNav = fund.latestNav?.let { toNavDTO(it) },
        )

    fun toNavDTO(nav: FundDailyNav): FundDailyNavDTO =
        FundDailyNavDTO(
            date = nav.date,
            closePrice = nav.closePrice.toDouble(),
            totalNetAssets = nav.totalNetAssets?.toDouble(),
        )

    fun toNavListDTO(navList: List<FundDailyNav>): List<FundDailyNavDTO> = navList.map { toNavDTO(it) }

    fun toMarketSnapshotDTO(snapshot: InvestmentFundMarketSnapshot): InvestmentFundMarketSnapshotDTO =
        InvestmentFundMarketSnapshotDTO(
            cnpj = snapshot.cnpj,
            assetType = snapshot.assetType,
            taxProfile = snapshot.taxProfile.name,
            latestNav = snapshot.latestNav?.let { toNavDTO(it) },
        )
}
