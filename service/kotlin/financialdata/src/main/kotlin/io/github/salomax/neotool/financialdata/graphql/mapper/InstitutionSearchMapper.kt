package io.github.salomax.neotool.financialdata.graphql.mapper

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionAutocomplete
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionSummary
import io.github.salomax.neotool.financialdata.graphql.dto.*
import jakarta.inject.Singleton

@Singleton
class InstitutionSearchMapper {
    fun toBacenInstitutionAutocompleteConnectionDTO(
        suggestions: List<BacenInstitutionAutocomplete>,
    ): BacenInstitutionAutocompleteConnectionDTO {
        return BacenInstitutionAutocompleteConnectionDTO(
            suggestions = suggestions.map { suggestion ->
                BacenInstitutionAutocompleteItemDTO(
                    id = suggestion.id,
                    name = suggestion.name,
                    cnpj = suggestion.cnpj,
                    legalName = suggestion.legalName,
                    tradeName = suggestion.tradeName,
                    logoUrl = suggestion.logoUrl,
                )
            },
        )
    }

    fun toBacenInstitutionConnectionDTO(
        connection: Connection<BacenInstitutionSummary>,
    ): BacenInstitutionConnectionDTO {
        return BacenInstitutionConnectionDTO(
            edges = connection.edges.map { edge ->
                BacenInstitutionEdgeDTO(
                    node = toBacenInstitutionSummaryItemDTO(edge.node),
                    cursor = edge.cursor,
                )
            },
            pageInfo = PageInfoDTO(
                hasNextPage = connection.pageInfo.hasNextPage,
                hasPreviousPage = connection.pageInfo.hasPreviousPage,
                startCursor = connection.pageInfo.startCursor,
                endCursor = connection.pageInfo.endCursor,
            ),
            totalCount = connection.totalCount?.toInt(),
        )
    }

    fun toBacenInstitutionSummaryItemDTO(
        summary: BacenInstitutionSummary,
    ): BacenInstitutionSummaryItemDTO {
        return BacenInstitutionSummaryItemDTO(
            id = summary.id,
            name = summary.name,
            legalName = summary.legalName,
            tradeName = summary.tradeName,
            logoUrl = summary.logoUrl,
            profit = summary.profit?.toDouble(),
            equity = summary.equity?.toDouble(),
            yoyEquityGrowth = summary.yoyEquityGrowth?.toDouble(),
            basileiaIndex = summary.basileiaIndex?.toDouble(),
            bacenInstitutionType = summary.bacenInstitutionType?.toString(),
            institutionType = summary.institutionType,
            tipoConsolidadoBancario = summary.tipoConsolidadoBancario,
        )
    }
}

