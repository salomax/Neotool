package io.github.salomax.neotool.financialdata.graphql.dto

import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

// Autocomplete DTOs
@Introspected
@Serdeable
data class BacenInstitutionAutocompleteConnectionDTO(
    val suggestions: List<BacenInstitutionAutocompleteItemDTO>,
)

@Introspected
@Serdeable
data class BacenInstitutionAutocompleteItemDTO(
    val id: String,
    val name: String,
    val cnpj: String?,
    val legalName: String?,
    val tradeName: String?,
    val assetId: String?,
)

// Full search DTOs
@Introspected
@Serdeable
data class BacenInstitutionConnectionDTO(
    val edges: List<BacenInstitutionEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int?,
)

@Introspected
@Serdeable
data class BacenInstitutionEdgeDTO(
    val node: BacenInstitutionSummaryItemDTO,
    val cursor: String,
)

@Introspected
@Serdeable
data class BacenInstitutionSummaryItemDTO(
    val id: String,
    val name: String,
    val legalName: String?,
    val tradeName: String?,
    val assetId: String?,
    val profit: Double?,
    val equity: Double?,
    val yoyEquityGrowth: Double?,
    val basileiaIndex: Double?,
    val bacenInstitutionType: String?,
    val institutionType: BacenInstitutionType?,
    val tipoConsolidadoBancario: String?,
)

@Introspected
@Serdeable
data class PageInfoDTO(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String?,
    val endCursor: String?,
)
