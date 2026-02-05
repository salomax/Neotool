package io.github.salomax.neotool.financialdata.graphql.mapper

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionSummary
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionAutocompleteItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionSummaryItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.DataRestrictionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.PageInfoDTO
import jakarta.inject.Singleton

@Singleton
class InstitutionSearchMapper {
    fun toBacenInstitutionAutocompleteConnectionDTO(
        suggestions: List<String>,
    ): BacenInstitutionAutocompleteConnectionDTO {
        return BacenInstitutionAutocompleteConnectionDTO(
            suggestions =
                suggestions
                    .map { name ->
                        BacenInstitutionAutocompleteItemDTO(
                            name = name,
                        )
                    },
        )
    }

    fun toBacenInstitutionConnectionDTO(
        connection: Connection<BacenInstitutionSummary>,
    ): BacenInstitutionConnectionDTO {
        return BacenInstitutionConnectionDTO(
            edges =
                connection.edges
                    .map { edge ->
                        BacenInstitutionEdgeDTO(
                            node = toBacenInstitutionSummaryItemDTO(edge.node),
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
    }

    fun toBacenInstitutionSummaryItemDTO(summary: BacenInstitutionSummary): BacenInstitutionSummaryItemDTO {
        return BacenInstitutionSummaryItemDTO(
            id = summary.id,
            name = summary.name,
            legalName = summary.legalName,
            tradeName = summary.tradeName,
            assetId = summary.assetId?.toString(),
            profit = summary.profit?.toDouble(),
            equity = summary.equity?.toDouble(),
            yoyEquityGrowth = summary.yoyEquityGrowth?.toDouble(),
            basileiaIndex = summary.basileiaIndex?.toDouble(),
            bacenInstitutionType = summary.bacenInstitutionType?.toString(),
            institutionType = summary.institutionType,
            tipoConsolidadoBancario = summary.tipoConsolidadoBancario,
            dataRestriction = toDataRestrictionDTO(summary.dataRestriction),
        )
    }

    private fun toDataRestrictionDTO(
        restriction: io.github.salomax.neotool.financialdata.domain.DataRestriction,
    ): DataRestrictionDTO {
        return DataRestrictionDTO(
            isRestricted = restriction.isRestricted,
            reason = restriction.reason,
        )
    }
}
